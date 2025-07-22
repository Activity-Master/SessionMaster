# SessionLoginService Implementation Guide for Reactive Migration

## Overview

This document provides a comprehensive guide for migrating the `SessionLoginService` class to reactive programming using Mutiny. The migration follows the guidance in the `SessionLoginService_Migration_Plan.md` file and applies the patterns observed in other successfully migrated services like `PasswordsService`.

## Migration Approach

The migration of `SessionLoginService` requires a comprehensive approach due to the interdependencies between methods. Changing one method to be reactive requires changing all the methods that call it or are called by it. This guide outlines the changes needed for each method, following the order specified in the migration plan:

1. First, migrate `updateLatestVisit()` to return `Uni<IInvolvedParty<?, ?>>`
2. Then, migrate `registerVisitor()` to return `Uni<UserConfirmationKeyDTO<?>>`
3. Next, fix the implementations of `loginVisitor()`, `logoutUser()`, and `loginUser()` to properly return `Uni<ProfileServiceDTO<?>>`
4. Add helper method `findOrCreateDeviceIP()` to simplify the `loginUser()` implementation

## Method-by-Method Migration Guide

### 1. `updateLatestVisit()` Method

**Current Implementation:**
```java
////@Transactional()
IInvolvedParty<?, ?> updateLatestVisit(IInvolvedParty<?, ?> newIp,
                                     java.util.UUID... identityToken)
{
    String lastVisit = convertToUTCDateTime(com.entityassist.RootEntity.getNow()).format(DateTimeFormatter.ISO_DATE);
    newIp.addOrUpdateClassification(LastVisitTime,
            null,
            lastVisit,
            system.get(),
            identityToken);
    return newIp;
}
```

**Reactive Implementation:**
```java
/**
 * Updates the last visit time for an involved party.
 * This method has been migrated to use reactive patterns.
 *
 * @param newIp The involved party to update
 * @param identityToken The identity tokens for security
 * @return A Uni emitting the updated involved party
 */
Uni<IInvolvedParty<?, ?>> updateLatestVisit(IInvolvedParty<?, ?> newIp,
                                     java.util.UUID... identityToken)
{
    log.log(Level.FINE, "Updating last visit time for involved party: {0}", newIp.getId());
    String lastVisit = convertToUTCDateTime(com.entityassist.RootEntity.getNow()).format(DateTimeFormatter.ISO_DATE);
    
    // Use ReactiveTransactionUtil for transaction management
    return ReactiveTransactionUtil.withTransaction(session -> {
        // Use addOrUpdateClassification which already returns a reactive type
        return newIp.addOrUpdateClassification(LastVisitTime,
                null,
                lastVisit,
                system.get(),
                identityToken)
            // Chain with Uni.createFrom().item(newIp) to return the involved party
            .chain(() -> Uni.createFrom().item(newIp))
            // Add error handling with onFailure().invoke()
            .onFailure().invoke(error -> log.log(Level.SEVERE, "Error updating last visit time: {0}", error.getMessage()));
    });
}
```

### 2. `registerVisitor()` Method

**Current Implementation:**
```java
@Override
//@Transactional()
public UserConfirmationKeyDTO<?> registerVisitor(UserRegistrationDTO<?> userRegistrationDTO, ISystems<?, ?> system, java.util.UUID... identityToken) throws UserExistsException, WaitingForConfirmationKeyException
{
    IInvolvedParty<?, ?> ipExists = involvedPartyService.get()
                                                      .builder()
                                                      .findByIdentificationType(IdentificationTypeUserName.toString(), userRegistrationDTO.getUserName(), system, identityToken)
                                                      .get()
                                                      .orElse(null);
    if (ipExists != null)
    {
        if (ipExists.hasClassifications(ConfirmationKey, null, system, identityToken))
        {
            throw new WaitingForConfirmationKeyException("The email address is waiting for a confirmation key");
        }
        throw new UserExistsException("That email address is already in use as a valid identifier");
    }
    //ActivityMasterConfiguration.get().setSecurityEnabled(false);
    IInvolvedParty<?, ?> newIp;
    
    ProfileServiceDTO<?> dto = com.guicedee.client.IGuiceContext.get(ProfileServiceDTO.class);
    
    newIp = dto.findInvolvedParty();
    
    var idType
            = newIp.addOrUpdateInvolvedPartyIdentificationType(NoClassification.toString(),
            IdentificationTypeUserName,
            userRegistrationDTO.getUserName(),
            userRegistrationDTO.getUserName(),
            system,
            this.identityToken);
    
    idType.expire(Duration.of(2, HOURS), identityToken);
    
    passwordsService.addUpdateUsernamePassword(userRegistrationDTO.getUserName(), userRegistrationDTO.getPassword(), newIp, system,
            this.identityToken);
    
    userRegistrationDTO.setPassword(null);
    var idUserNameType
            = newIp.addOrUpdateInvolvedPartyIdentificationType(NoClassification.toString(),
            IdentificationTypeUserName,
            userRegistrationDTO.getUserName(),
            userRegistrationDTO.getUserName(),
            system,
            this.identityToken);
    idUserNameType.expire(Duration.of(2, HOURS), identityToken);
    
    Optional<? extends IRelationshipValue<?, IClassification<?, ?>, ?>> classification = newIp.findClassification(SecurityPassword, system, identityToken);
    Optional<? extends IRelationshipValue<?, IClassification<?, ?>, ?>> classification1 = newIp.findClassification(SecurityPasswordSalt, system, identityToken);
    
    classification.get()
                  .expire(Duration.of(2, HOURS), this.identityToken);
    classification1.get()
                   .expire(Duration.of(2, HOURS), this.identityToken);
    
    UserConfirmationKeyDTO<?> confirmationKeyDTO = new UserConfirmationKeyDTO<>()
            .setWebClientUUID(userRegistrationDTO.getWebClientUUID())
            .setIdentityToken(newIp.getId());
    confirmationKeyDTO.setConfirmationKey(String.valueOf(UUID.randomUUID()));
    newIp.addOrUpdateClassification(ConfirmationKey, null, confirmationKeyDTO.getConfirmationKey() + "", system, this.identityToken);
    newIp.findClassification(ConfirmationKey,system,identityToken).ifPresent(x->x.expire(Duration.of(2, HOURS), this.identityToken));
    return confirmationKeyDTO;
}
```

**Reactive Implementation:**
```java
@Override
public Uni<UserConfirmationKeyDTO<?>> registerVisitor(UserRegistrationDTO<?> userRegistrationDTO, ISystems<?, ?> system, java.util.UUID... identityToken)
{
    log.log(Level.FINE, "Registering visitor: {0}", userRegistrationDTO.getUserName());
    
    // Use ReactiveTransactionUtil for transaction management
    return ReactiveTransactionUtil.withTransaction(session -> {
        // Check if user already exists
        return involvedPartyService.get().builder()
            .findByIdentificationType(IdentificationTypeUserName.toString(), userRegistrationDTO.getUserName(), system, identityToken)
            .get()
            .onItem().ifNotNull().transformToUni(ipExists -> {
                // Check if user has confirmation key
                return ipExists.hasClassifications(ConfirmationKey, null, system, identityToken)
                    .chain(hasConfirmationKey -> {
                        if (hasConfirmationKey) {
                            return Uni.createFrom().failure(
                                new WaitingForConfirmationKeyException("The email address is waiting for a confirmation key"));
                        } else {
                            return Uni.createFrom().failure(
                                new UserExistsException("That email address is already in use as a valid identifier"));
                        }
                    });
            })
            .onItem().ifNull().switchTo(() -> {
                // Create new involved party
                ProfileServiceDTO<?> dto = com.guicedee.client.IGuiceContext.get(ProfileServiceDTO.class);
                IInvolvedParty<?, ?> newIp = dto.findInvolvedParty();
                
                // Add identification type
                return newIp.addOrUpdateInvolvedPartyIdentificationType(
                        NoClassification.toString(),
                        IdentificationTypeUserName,
                        userRegistrationDTO.getUserName(),
                        userRegistrationDTO.getUserName(),
                        system,
                        this.identityToken)
                    .chain(idType -> {
                        // Expire identification type
                        return idType.expire(Duration.of(2, HOURS), identityToken)
                            .chain(() -> {
                                // Add username and password
                                return passwordsService.addUpdateUsernamePassword(
                                    userRegistrationDTO.getUserName(), 
                                    userRegistrationDTO.getPassword(), 
                                    newIp, 
                                    system,
                                    this.identityToken);
                            });
                    })
                    .chain(() -> {
                        // Clear password for security
                        userRegistrationDTO.setPassword(null);
                        
                        // Add username identification type
                        return newIp.addOrUpdateInvolvedPartyIdentificationType(
                            NoClassification.toString(),
                            IdentificationTypeUserName,
                            userRegistrationDTO.getUserName(),
                            userRegistrationDTO.getUserName(),
                            system,
                            this.identityToken);
                    })
                    .chain(idUserNameType -> {
                        // Expire username identification type
                        return idUserNameType.expire(Duration.of(2, HOURS), identityToken);
                    })
                    .chain(() -> {
                        // Find security password classifications
                        Uni<IRelationshipValue<?, IClassification<?, ?>, ?>> passwordUni = 
                            newIp.findClassification(SecurityPassword, system, identityToken)
                                .onItem().ifNull().failWith(() -> new RuntimeException("Security password not found"));
                                
                        Uni<IRelationshipValue<?, IClassification<?, ?>, ?>> saltUni = 
                            newIp.findClassification(SecurityPasswordSalt, system, identityToken)
                                .onItem().ifNull().failWith(() -> new RuntimeException("Security password salt not found"));
                                
                        // Combine password and salt operations
                        return Uni.combine().all().unis(passwordUni, saltUni)
                            .asTuple()
                            .chain(tuple -> {
                                IRelationshipValue<?, IClassification<?, ?>, ?> password = tuple.getItem1();
                                IRelationshipValue<?, IClassification<?, ?>, ?> salt = tuple.getItem2();
                                
                                // Expire password and salt
                                Uni<?> expirePassword = password.expire(Duration.of(2, HOURS), this.identityToken);
                                Uni<?> expireSalt = salt.expire(Duration.of(2, HOURS), this.identityToken);
                                
                                return Uni.combine().all().unis(expirePassword, expireSalt)
                                    .discardItems();
                            });
                    })
                    .chain(() -> {
                        // Create confirmation key
                        UserConfirmationKeyDTO<?> confirmationKeyDTO = new UserConfirmationKeyDTO<>()
                            .setWebClientUUID(userRegistrationDTO.getWebClientUUID())
                            .setIdentityToken(newIp.getId());
                        confirmationKeyDTO.setConfirmationKey(String.valueOf(UUID.randomUUID()));
                        
                        // Add confirmation key classification
                        return newIp.addOrUpdateClassification(
                                ConfirmationKey, 
                                null, 
                                confirmationKeyDTO.getConfirmationKey() + "", 
                                system, 
                                this.identityToken)
                            .chain(() -> {
                                // Find and expire confirmation key
                                return newIp.findClassification(ConfirmationKey, system, identityToken)
                                    .chain(classification -> {
                                        if (classification != null) {
                                            return classification.expire(Duration.of(2, HOURS), this.identityToken)
                                                .map(v -> confirmationKeyDTO);
                                        } else {
                                            return Uni.createFrom().item(confirmationKeyDTO);
                                        }
                                    });
                            });
                    });
            })
            .onFailure().invoke(error -> log.log(Level.SEVERE, "Error registering visitor: {0}", error.getMessage()));
    });
}
```

### 3. `loginVisitor()` Method

**Current Implementation:**
```java
@Override
public Uni<ProfileServiceDTO<?>> loginVisitor(ProfileServiceDTO<?> profileServiceDTO, ISystems<?, ?> system, java.util.UUID... identityToken)
{
    if ((identityToken == null || identityToken.length == 0) && profileServiceDTO.getIdentityToken() == null)
    {
        identityToken = new UUID[]{this.identityToken};
    }
    ProfileServiceDTO<?> dto = profileServiceDTO;
    IInvolvedParty<?, ?> iInvolvedParty = involvedPartyService.get();
    IInvolvedParty<?, ?> deviceIP = iInvolvedParty.builder()
                                                .findByType(TypeDevice.toString(), dto.getWebClientUUID()
                                                                                      .toString(),
                                                system, identityToken)
                                                .get()
                                                .orElse(null);
    if (deviceIP == null)
    {
        deviceIP = createDeviceIP(profileServiceDTO);
        //new device there will be nothing else ever
        profileServiceDTO.setInvolvedParty(deviceIP);
        profileServiceDTO.setIdentityToken(deviceIP.getId());
        /*configureFromReadableUserAgent(deviceIP, com.guicedee.client.IGuiceContext.get(ReadableUserAgent.class), system, identityToken);
        try
        {
            HttpServletRequest request = get(GuicedServletKeys.getHttpServletRequestKey());
            configureFromHTTPServletRequest(profileServiceDTO, deviceIP, system, request, enterprise);
        }
        catch (Throwable T)
        {
            log.log(Level.FINER, "Unable to log servlet request information", T);
        }*/
    }
    //search by web client uuid
    IInvolvedParty<?, ?> foundIPCurrentOnDevice = dto.findInvolvedParty();
    if (foundIPCurrentOnDevice == null)
    {
        //new device there will be nothing else ever
        profileServiceDTO.setInvolvedParty(deviceIP);
        profileServiceDTO.setIdentityToken(deviceIP.getId());
        foundIPCurrentOnDevice = deviceIP;
    }
    else
    {
        profileServiceDTO.setInvolvedParty(foundIPCurrentOnDevice);
        profileServiceDTO.setIdentityToken(foundIPCurrentOnDevice.getId());
    }
    updateLatestVisit(foundIPCurrentOnDevice, identityToken);
    
    sessionMasterService.getSession(foundIPCurrentOnDevice, system, identityToken);
    return profileServiceDTO;
}
```

**Reactive Implementation:**
```java
@Override
public Uni<ProfileServiceDTO<?>> loginVisitor(ProfileServiceDTO<?> profileServiceDTO, ISystems<?, ?> system, java.util.UUID... identityToken)
{
    log.log(Level.FINE, "Login visitor with web client UUID: {0}", profileServiceDTO.getWebClientUUID());
    
    // Handle identity token
    if ((identityToken == null || identityToken.length == 0) && profileServiceDTO.getIdentityToken() == null)
    {
        identityToken = new UUID[]{this.identityToken};
    }
    final UUID[] finalIdentityToken = identityToken;
    
    // Use ReactiveTransactionUtil for transaction management
    return ReactiveTransactionUtil.withTransaction(session -> {
        ProfileServiceDTO<?> dto = profileServiceDTO;
        IInvolvedParty<?, ?> iInvolvedParty = involvedPartyService.get();
        
        // Find device IP
        return iInvolvedParty.builder()
            .findByType(TypeDevice.toString(), dto.getWebClientUUID().toString(), system, finalIdentityToken)
            .get()
            .onItem().ifNull().switchTo(() -> {
                // Create device IP if not found
                return createDeviceIP(profileServiceDTO)
                    .map(deviceIP -> {
                        // Set involved party and identity token
                        profileServiceDTO.setInvolvedParty(deviceIP);
                        profileServiceDTO.setIdentityToken(deviceIP.getId());
                        return deviceIP;
                    });
            })
            .chain(deviceIP -> {
                // Find involved party
                IInvolvedParty<?, ?> foundIPCurrentOnDevice = dto.findInvolvedParty();
                
                if (foundIPCurrentOnDevice == null) {
                    // Set device IP as involved party
                    profileServiceDTO.setInvolvedParty(deviceIP);
                    profileServiceDTO.setIdentityToken(deviceIP.getId());
                    foundIPCurrentOnDevice = deviceIP;
                } else {
                    // Set found involved party
                    profileServiceDTO.setInvolvedParty(foundIPCurrentOnDevice);
                    profileServiceDTO.setIdentityToken(foundIPCurrentOnDevice.getId());
                }
                
                final IInvolvedParty<?, ?> finalFoundIPCurrentOnDevice = foundIPCurrentOnDevice;
                
                // Update last visit time
                return updateLatestVisit(finalFoundIPCurrentOnDevice, finalIdentityToken)
                    .chain(updatedIP -> {
                        // Get session
                        return sessionMasterService.getSession(updatedIP, system, finalIdentityToken)
                            .map(session -> {
                                // Return profile service DTO
                                return profileServiceDTO;
                            });
                    });
            })
            .onFailure().invoke(error -> log.log(Level.SEVERE, "Error in loginVisitor: {0}", error.getMessage()));
    });
}
```

### 4. Helper Method: `findOrCreateDeviceIP()`

This helper method simplifies the implementation of `loginUser()` by encapsulating the logic for finding or creating a device IP:

```java
/**
 * Finds or creates a device IP for the given profile service DTO.
 * This method has been added to simplify the loginUser implementation.
 *
 * @param profileServiceDTO The profile service DTO containing client information
 * @param system The system requesting the operation
 * @param identityToken The identity tokens for security
 * @return A Uni emitting the found or created device IP
 */
private Uni<IInvolvedParty<?, ?>> findOrCreateDeviceIP(ProfileServiceDTO<?> profileServiceDTO, ISystems<?, ?> system, java.util.UUID... identityToken)
{
    log.log(Level.FINE, "Finding or creating device IP for web client UUID: {0}", profileServiceDTO.getWebClientUUID());
    
    if (profileServiceDTO.getWebClientUUID() == null) {
        profileServiceDTO.setWebClientUUID(UUID.randomUUID());
    }
    
    // Use ReactiveTransactionUtil for transaction management
    return ReactiveTransactionUtil.withTransaction(session -> {
        // Try to find existing device IP
        return involvedPartyService.get().builder()
            .findByTypeAll(TypeDevice.toString(), profileServiceDTO.getWebClientUUID().toString(), system, identityToken)
            .latestFirst()
            .setMaxResults(1)
            .get()
            .onItem().ifNotNull().transform(ip -> (IInvolvedParty<?, ?>) ip)
            .onItem().ifNull().switchTo(() -> {
                // Create new device IP if not found
                log.log(Level.FINE, "Device IP not found, creating new one via loginVisitor");
                return loginVisitor(profileServiceDTO, system, identityToken)
                    .chain(dto -> {
                        // Find device IP again
                        return involvedPartyService.get().builder()
                            .findByTypeAll(TypeDevice.toString(), profileServiceDTO.getWebClientUUID().toString(), system, identityToken)
                            .latestFirst()
                            .setMaxResults(1)
                            .get()
                            .onItem().ifNull().failWith(() -> new InvolvedPartyException("Device IP must already exist before attempting to login"));
                    });
            })
            .onFailure().invoke(error -> log.log(Level.SEVERE, "Error finding or creating device IP: {0}", error.getMessage()));
    });
}
```

## Implementation Strategy

To successfully migrate the `SessionLoginService` class to reactive programming, follow these steps:

1. **Create a New Branch**: Create a new branch for the migration to avoid disrupting the main codebase.

2. **Implement Changes in Order**:
   - First, migrate `updateLatestVisit()` to return `Uni<IInvolvedParty<?, ?>>`
   - Then, migrate `registerVisitor()` to return `Uni<UserConfirmationKeyDTO<?>>`
   - Add the helper method `findOrCreateDeviceIP()` to simplify the `loginUser()` implementation
   - Next, fix the implementations of `loginVisitor()`, `logoutUser()`, and `loginUser()` to properly return `Uni<ProfileServiceDTO<?>>`

3. **Key Reactive Patterns to Use**:
   - Use `ReactiveTransactionUtil.withTransaction()` for transaction management
   - Use `chain()` for sequential operations
   - Use `map()` for transforming results
   - Use `Uni.combine().all().unis()` for parallel operations
   - Use `onFailure().invoke()` for error logging
   - Use `onFailure().transform()` for error transformation
   - Use `onItem().ifNull().switchTo()` for handling null values

4. **Testing Strategy**:
   - Test each method individually
   - Test the entire class to ensure all methods work together correctly
   - Verify that all methods properly handle errors and edge cases

## Conclusion

Migrating the `SessionLoginService` class to reactive programming is a complex task due to the interdependencies between methods. This guide provides a comprehensive approach to the migration, following the guidance in the `SessionLoginService_Migration_Plan.md` file and applying the patterns observed in other successfully migrated services like `PasswordsService`.

By following this guide, you can ensure that the `SessionLoginService` class is fully migrated to reactive programming, with all methods properly returning reactive types and using reactive composition throughout.