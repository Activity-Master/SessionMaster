package com.guicedee.activitymaster.sessions.test;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import java.sql.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import static org.junit.jupiter.api.Assertions.*;
/** Exercises the production atomic query on disposable PostgreSQL with independent connections. */
class SessionValueAtomicUpdateTest {
 static PostgreSQLContainer<?> db; static String sql; static final ObjectMapper JSON=new ObjectMapper();
 @BeforeAll static void start()throws Exception {
  String source=Files.readString(Path.of("src/main/java/com/guicedee/activitymaster/sessions/UserSessionService.java"));
  var m=Pattern.compile("ATOMIC_VALUE_UPDATE = \"\"\"(.*?)\"\"\"",Pattern.DOTALL).matcher(source);assertTrue(m.find());sql=m.group(1).stripIndent();
  db=new PostgreSQLContainer<>("postgres:17-alpine").withDatabaseName("sessions");db.start();
  try(var c=connection();var s=c.createStatement()){s.execute("create schema resource");s.execute("create table resource.resourceitemdatavalue(resourceitemdatavalueid uuid primary key,resourceitemdatavalue bytea not null)");}
 }
 @AfterAll static void stop(){if(db!=null)db.stop();}
 static Connection connection()throws Exception{return DriverManager.getConnection(db.getJdbcUrl(),db.getUsername(),db.getPassword());}
 static UUID row()throws Exception {
  UUID id=UUID.randomUUID();try(var c=connection();var s=c.prepareStatement("insert into resource.resourceitemdatavalue values (?,?)")){s.setObject(1,id);s.setBytes(2,"{\"unrelated.language\":\"日本語\"}".getBytes(StandardCharsets.UTF_8));s.executeUpdate();}return id;
 }
 static int cas(Connection c,UUID id,String key,String expected,String next)throws Exception {
  Map<String,Object> v=new HashMap<>();v.put("dataValueId",id);v.put("preferenceKey",key);v.put("expected",expected);v.put("replacement",next);
  var args=new ArrayList<Object>();var m=Pattern.compile("(?<!:):([A-Za-z][A-Za-z0-9_]*)").matcher(sql);var q=new StringBuilder();
  while(m.find()){args.add(v.get(m.group(1)));m.appendReplacement(q,"?");}m.appendTail(q);
  try(var s=c.prepareStatement(q.toString())){for(int i=0;i<args.size();i++)s.setObject(i+1,args.get(i));return s.executeUpdate();}
 }
 static String value(UUID id,String key)throws Exception {
  try(var c=connection();var s=c.prepareStatement("select resourceitemdatavalue from resource.resourceitemdatavalue where resourceitemdatavalueid=?")){s.setObject(1,id);try(var r=s.executeQuery()){assertTrue(r.next());return JSON.readTree(r.getBytes(1)).path(key).asString(null);}}
 }
 @Test void differentPluginUpdatesRetainBothValuesAndUnrelatedData()throws Exception {
  UUID id=row();var start=new CountDownLatch(1);try(var pool=Executors.newFixedThreadPool(2)) {
   var a=pool.submit(()->{start.await();try(var c=connection()){return cas(c,id,"chess",null,"sage");}});
   var b=pool.submit(()->{start.await();try(var c=connection()){return cas(c,id,"other",null,"keep");}});
   start.countDown();assertEquals(1,a.get(15,TimeUnit.SECONDS));assertEquals(1,b.get(15,TimeUnit.SECONDS));
  }assertEquals("sage",value(id,"chess"));assertEquals("keep",value(id,"other"));assertEquals("日本語",value(id,"unrelated.language"));
 }
 @Test void samePluginRaceHasOneWinnerAndStaleWriteIsRejected()throws Exception {
  UUID id=row();var start=new CountDownLatch(1);try(var pool=Executors.newFixedThreadPool(2)) {
   var a=pool.submit(()->{start.await();try(var c=connection()){return cas(c,id,"chess",null,"a");}});
   var b=pool.submit(()->{start.await();try(var c=connection()){return cas(c,id,"chess",null,"b");}});
   start.countDown();assertEquals(1,a.get(15,TimeUnit.SECONDS)+b.get(15,TimeUnit.SECONDS));
  }try(var c=connection()){assertEquals(0,cas(c,id,"chess",null,"stale"));}assertNotEquals("stale",value(id,"chess"));assertEquals("日本語",value(id,"unrelated.language"));
 }
 @Test void rollbackAndMalformedOuterDocumentDoNotLoseData()throws Exception {
  UUID id=row();try(var c=connection()){c.setAutoCommit(false);assertEquals(1,cas(c,id,"chess",null,"pending"));c.rollback();}
  assertNull(value(id,"chess"));assertEquals("日本語",value(id,"unrelated.language"));
  try(var c=connection();var s=c.prepareStatement("update resource.resourceitemdatavalue set resourceitemdatavalue=? where resourceitemdatavalueid=?")){s.setBytes(1,"[]".getBytes(StandardCharsets.UTF_8));s.setObject(2,id);s.executeUpdate();assertEquals(0,cas(c,id,"chess",null,"unsafe"));}
 }
}
