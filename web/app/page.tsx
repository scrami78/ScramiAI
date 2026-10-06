"use client";

import { useState } from "react";

type Message = { role:"user"|"assistant"; text:string };

export default function Home() {
  const [messages,setMessages]=useState<Message[]>([]);
  const [text,setText]=useState("");
  const [busy,setBusy]=useState(false);

  async function send() {
    const message=text.trim();
    if(!message || busy) return;
    const next=[...messages,{role:"user" as const,text:message}];
    setMessages(next); setText(""); setBusy(true);
    try {
      const res=await fetch("/api/chat",{method:"POST",headers:{"content-type":"application/json"},
        body:JSON.stringify({message,history:next.map(m=>m.role.toUpperCase()+": "+m.text).join("\n")})});
      const data=await res.json();
      setMessages([...next,{role:"assistant",text:data.text||data.error||"S.AI не смог ответить."}]);
    } catch {
      setMessages([...next,{role:"assistant",text:"Не удалось подключиться к S.AI Core. Запусти локальный gateway на ПК."}]);
    } finally { setBusy(false); }
  }

  return <div className="shell">
    <aside className="side">
      <div className="brand"><div className="mark">S.AI</div><span>S.AI</span></div>
      <button className="new" onClick={()=>setMessages([])}>＋ Новый чат</button>
      <div className="history">{messages.length ? "Текущий чат" : "История появится здесь"}</div>
      <div style={{marginTop:"auto"}} className="history">Local-first • Free</div>
    </aside>
    <main className="main">
      <header className="top"><span className="model">S.AI</span><span className="pill">SMART • LOCAL CORE</span></header>
      <section className="feed"><div className="wrap">
        {!messages.length && <div className="welcome"><h1>Чем могу помочь?</h1><p>Твой личный S.AI — без обязательного платного API.</p></div>}
        {messages.map((m,i)=><div className={"msg "+m.role} key={i}><div className="avatar">{m.role==="assistant"?"S":"YOU"}</div><div className="bubble">{m.text}</div></div>)}
      </div></section>
      <div className="composerArea"><div className="composer">
        <div className="tools"><button className="tool" type="button">＋</button></div>
        <textarea value={text} onChange={e=>setText(e.target.value)} onKeyDown={e=>{if(e.key==="Enter"&&!e.shiftKey){e.preventDefault();send()}}} placeholder="Сообщение S.AI"/>
        <button className="icon" type="button">◉</button>
        <button className="icon send" onClick={send} disabled={busy}>↑</button>
      </div></div>
    </main>
  </div>;
}
