import { NextRequest, NextResponse } from "next/server";

export async function POST(req: NextRequest) {
  const body = await req.json();
  const base = (process.env.SAI_GATEWAY_URL || "http://127.0.0.1:8787").replace(/\/$/, "");
  try {
    const upstream = await fetch(base + "/v1/chat", {
      method:"POST",
      headers:{"content-type":"application/json"},
      body:JSON.stringify(body),
      cache:"no-store"
    });
    const text = await upstream.text();
    return new NextResponse(text,{status:upstream.status,headers:{"content-type":"application/json"}});
  } catch (error) {
    return NextResponse.json({error:String(error)},{status:502});
  }
}
