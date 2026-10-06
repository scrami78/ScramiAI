package com.scrami.nosignal
import android.app.Activity
import android.os.Bundle
import android.graphics.*
import android.view.*
import android.content.Context
import kotlin.math.*

class MainActivity: Activity(){
 override fun onCreate(b:Bundle?){super.onCreate(b);window.setFlags(1024,1024);setContentView(Game(this))}
}
class Game(ctx:Context):View(ctx){
 val p=Paint(3); var screen=0; var x=150f; var y=350f
 var hp=100; var bat=100; var fear=0; var quest=1; var phone=false
 var enemy=false; var ex=850f; var ey=350f; var msg=0
 fun t(c:Canvas,s:String,a:Float,b:Float,z:Float,col:Int=Color.WHITE){p.color=col;p.textSize=z;p.style=Paint.Style.FILL;c.drawText(s,a,b,p)}
 fun r(c:Canvas,a:Float,b:Float,d:Float,e:Float,col:Int,st:Boolean=false){p.color=col;p.style=if(st)Paint.Style.STROKE else Paint.Style.FILL;c.drawRoundRect(a,b,d,e,14f,14f,p);p.style=Paint.Style.FILL}
 override fun onDraw(c:Canvas){c.drawColor(Color.rgb(4,4,5));if(screen==0)menu(c) else if(screen==1)world(c) else end(c)}
 fun menu(c:Canvas){t(c,"SCRAMI",65f,95f,28f,Color.LTGRAY);t(c,"NO SIGNAL",65f,160f,58f);t(c,"THE CITY FORGOT YOU.",68f,190f,14f,Color.GRAY);r(c,65f,240f,390f,310f,Color.WHITE);t(c,"NEW GAME",155f,282f,22f,Color.BLACK);r(c,65f,330f,390f,390f,Color.rgb(18,18,20),true);t(c,"CONTINUE",150f,368f,20f);t(c,"0.2 • SURVIVAL PROTOTYPE",65f,445f,12f,Color.GRAY)}
 fun world(c:Canvas){
  p.color=Color.rgb(10,10,12);c.drawRect(0f,0f,1100f,700f,p);p.color=Color.rgb(13,13,15);c.drawRect(0f,270f,1100f,450f,p)
  for(i in 0..5){r(c,30+i*180f,80f,160+i*180f,215f,Color.rgb(20,20,23));t(c,"BLOCK "+(i+1),48+i*180f,105f,10f,Color.GRAY)}
  for(i in 0..8){p.color=Color.rgb(45,45,48);c.drawRect(i*135f,355f,i*135f+65f,360f,p)}
  if(quest==1){r(c,820f,120f,865f,175f,Color.WHITE);t(c,"+",834f,157f,27f,Color.BLACK)}
  p.color=Color.WHITE;c.drawCircle(x,y,17f,p);p.color=Color.DKGRAY;c.drawCircle(x,y,6f,p)
  if(enemy){p.color=Color.rgb(180,25,30);c.drawCircle(ex,ey,24f,p);t(c,"!",ex-5,ey+8,25f)}
  r(c,15f,12f,400f,58f,Color.rgb(8,8,10),true);t(c,"HP $hp",28f,42f,15f);t(c,"BAT $bat%",100f,42f,15f);t(c,"FEAR $fear",190f,42f,15f)
  r(c,910f,12f,1050f,58f,Color.rgb(20,20,22),true);t(c,"PHONE",930f,42f,13f)
  val cc=Color.rgb(18,18,20);r(c,25f,530f,105f,605f,cc,true);t(c,"←",49f,577f,30f);r(c,120f,530f,200f,605f,cc,true);t(c,"→",144f,577f,30f);r(c,72f,455f,152f,520f,cc,true);t(c,"↑",103f,495f,27f);r(c,72f,615f,152f,680f,cc,true);t(c,"↓",103f,655f,27f)
  t(c,when(quest){1->"Find the battery.";2->"Reach the old radio.";3->"Run back to the exit.";else->"Explore."},420f,625f,16f,Color.LTGRAY)
  if(phone)phone(c)
 }
 fun phone(c:Canvas){r(c,365f,65f,900f,625f,Color.rgb(7,7,9));r(c,365f,65f,900f,625f,Color.WHITE,true);t(c,"PHONE",400f,110f,22f);t(c,"UNKNOWN",400f,160f,13f,Color.GRAY);val a=if(msg==0)listOf("You shouldn't be here.","Don't trust the lights.","Go east. Find the battery.") else if(msg==1)listOf("You found it.","Good.","Now the radio. Hurry.") else listOf("THE SIGNAL IS CLOSE.","TURN AROUND.","RUN.");a.forEachIndexed{i,s->t(c,s,405f,205f+i*43,18f)}}
 fun end(c:Canvas){t(c,if(hp<=0)"SIGNAL LOST" else "SIGNAL FOUND",90f,160f,50f);t(c,if(hp<=0)"Something reached you." else "The city finally answered.",95f,205f,18f,Color.LTGRAY);r(c,90f,270f,390f,335f,Color.WHITE);t(c,"PLAY AGAIN",160f,310f,20f,Color.BLACK)}
 override fun onTouchEvent(e:MotionEvent):Boolean{
  if(e.action!=MotionEvent.ACTION_DOWN)return true
  val a=e.x;val b=e.y
  if(screen==0){if(b in 220f..330f){screen=1;reset()};invalidate();return true}
  if(screen==2){screen=0;invalidate();return true}
  if(phone){if(!(a in 350f..910f&&b in 50f..640f))phone=false;invalidate();return true}
  if(a>880&&b<90){phone=true;invalidate();return true}
  val s=32f
  if(a<110&&b>510)x-=s else if(a<220&&b>510)x+=s else if(a in 60f..165f&&b in 440f..525f)y-=s else if(a in 60f..165f&&b>600)y+=s
  x=x.coerceIn(20f,1070f);y=y.coerceIn(70f,680f)
  if(quest==1&&x>760&&y in 80f..230f){quest=2;bat=100;msg=1;enemy=true;fear=20}
  if(quest==2&&x>900&&y in 250f..500f){quest=3;msg=2;fear=55}
  if(quest==3&&x<130&&y in 250f..500f){screen=2}
  if(enemy){ex+=(x-ex)*.025f;ey+=(y-ey)*.025f;if(hypot(x-ex,y-ey)<48){hp-=12;fear=min(100,fear+12)};bat=max(0,bat-1);if(hp<=0)screen=2}
  invalidate();return true
 }
 fun reset(){x=150f;y=350f;hp=100;bat=100;fear=0;quest=1;phone=false;enemy=false;ex=850f;ey=350f;msg=0}
}