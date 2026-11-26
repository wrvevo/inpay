import { useEffect, useState } from "react";
type Case = { id:string; storeId:string; cnpj:string; legalName:string; storeName:string; state:string; bindings:Record<string,string>; partners:Record<string,string>; accountsReceipt:string; demoId?:string; tests:Record<string,string> };
type Doc = { id:string; kind:string; version:number; decision:string; scanSource:string };
const kinds = [["company","Empresa"],["representative","Representante"],["address","Endereço"],["bank","Conta bancária"]];
const methods = [["credit_card","Crédito"],["debit_card","Débito"],["pix","Pix"],["boleto","Boleto"],["transfer","Transferência"]];
const cards = ["cielo","rede","getnet","stone","bin","global-payments","pagbank","safrapay"];
const banks = ["c6","itau","santander","bb","bradesco","caixa","banrisul"];
export default function ConnectedHomologation({user,onLogout}:{user:{token:string;role:string;name:string};onLogout:()=>void}) {
  const [cases,setCases]=useState<Case[]>([]);
  const [current,setCurrent]=useState<Case|null>(null);
  const [docs,setDocs]=useState<Doc[]>([]);
  const [audit,setAudit]=useState<Record<string,unknown>[]>([]);
  const [bindings,setBindings]=useState<Record<string,string>>({});
  const [error,setError]=useState("");
  const [busy,setBusy]=useState(false);
  const [kind,setKind]=useState("company");
  const [file,setFile]=useState<File|null>(null);
  const write=["PDV","ADMIN"].includes(user.role);
  const approve=["BACKOFFICE","ADMIN"].includes(user.role);
  async function api<T>(path:string,method="GET",body?:unknown):Promise<T> {
    const response=await fetch("/api/painel/homologation/v2"+path,{method,headers:{Authorization:"Bearer "+user.token,"Content-Type":"application/json"},body:body===undefined?undefined:JSON.stringify(body),cache:"no-store"});
    if(!response.ok) throw new Error("Operação não confirmada (HTTP "+response.status+"). Verifique os pré-requisitos, o perfil e os serviços.");
    return response.json();
  }
  async function select(id:string) {
    const [item,documents,events]=await Promise.all([api<Case>("/cases/"+id),api<{data:Doc[]}>("/cases/"+id+"/documents"),api<{data:Record<string,unknown>[]}>("/cases/"+id+"/audit")]);
    setCurrent(item);setDocs(documents.data);setAudit(events.data);setBindings(item.bindings);
  }
  async function list(){setCases((await api<{data:Case[]}>("/cases")).data);}
  async function run(action:()=>Promise<void>){setBusy(true);setError("");try{await action();}catch(e){setError((e as Error).message);}finally{setBusy(false);}}
  useEffect(()=>{void run(list);},[user.token]);
  const action=(name:string,body:unknown={})=>run(async()=>{if(!current)return;await api("/cases/"+current.id+"/"+name,"POST",body);await list();await select(current.id);});
  async function upload(fixture:boolean) {
    if(!current)return;
    let data:string;
    if(fixture){data=btoa("%PDF-1.4\n% INPAY-SYNTHETIC-DOCUMENT\n%%EOF\n");}
    else {if(!file||file.size>2*1024*1024)throw new Error("Selecione PDF, PNG ou JPEG de até 2 MiB.");const bytes=new Uint8Array(await file.arrayBuffer());let binary="";for(let i=0;i<bytes.length;i+=8192)binary+=String.fromCharCode(...bytes.subarray(i,i+8192));data=btoa(binary);}
    await api("/cases/"+current.id+"/documents","POST",{kind,data});setFile(null);await select(current.id);
  }
  async function download(doc:Doc) {if(!current)return;const result=await api<{mime:string;data:string}>("/cases/"+current.id+"/documents/"+doc.id+"/download");const bytes=Uint8Array.from(atob(result.data),c=>c.charCodeAt(0));const url=URL.createObjectURL(new Blob([bytes],{type:result.mime}));const a=document.createElement("a");a.href=url;a.download=doc.kind+"-"+doc.version;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}
  return <main className="form" style={{maxWidth:1100,margin:"auto",padding:24}}>
    <header className="card"><h1>InPay Homologação</h1><p>{user.name} · {user.role}</p><button onClick={onLogout}>Sair</button><p>Laboratório. Não envie dados reais sem autorização e configuração de proteção. A aprovação não ativa produção.</p></header>
    {error&&<p role="alert" className="error">{error}</p>}
    <section className="card form"><h2>Processos</h2><button disabled={busy} onClick={()=>void run(list)}>Atualizar</button>{cases.map(c=><button key={c.id} disabled={busy} onClick={()=>void run(()=>select(c.id))}>{c.storeName} · {c.state}</button>)}</section>
    {write&&<form className="card form" onSubmit={e=>{e.preventDefault();const form=Object.fromEntries(new FormData(e.currentTarget));void run(async()=>{const item=await api<Case>("/cases","POST",form);await list();await select(item.id);});}}><h2>Novo cadastro</h2>{[["cnpj","CNPJ"],["legalName","Razão social"],["storeName","Loja"],["address","Endereço"]].map(([n,l])=><label key={n}>{l}<input name={n} required/></label>)}<button className="primary" disabled={busy}>Criar processo</button></form>}
    {current&&<section className="card form"><h2>{current.storeName} — {current.state}</h2><p>{current.legalName} · {current.cnpj}</p><p>Loja: {current.storeId}</p>
    <h3>Meios de pagamento</h3>{methods.map(([m,label])=><label key={m}>{label}<select disabled={busy||!write||current.state!=="DRAFT"} value={bindings[m]||""} onChange={e=>{const value=e.target.value;setBindings(previous=>{const next={...previous};if(value)next[m]=value;else delete next[m];return next;});}}><option value="">Não habilitado</option>{(m.includes("card")?cards:banks.filter(b=>m!=="transfer"||b!=="c6")).map(b=><option key={b}>{b}</option>)}</select></label>)}
    {write&&current.state==="DRAFT"&&<button disabled={busy} onClick={()=>void run(async()=>{await api("/cases/"+current.id+"/bindings","PUT",{bindings});await select(current.id);})}>Salvar configuração</button>}
    <h3>Documentação</h3>{docs.map(d=><div key={d.id}><strong>{kinds.find(k=>k[0]===d.kind)?.[1]} · v{d.version}</strong> — {d.decision} · {d.scanSource} {approve&&<button disabled={busy} onClick={()=>void run(()=>download(d))}>Baixar</button>}{approve&&current.state==="DOCUMENT_REVIEW"&&<><button disabled={busy} onClick={()=>void action("documents/"+d.id+"/review",{approve:true})}>Aprovar</button><button disabled={busy} onClick={()=>void action("documents/"+d.id+"/review",{approve:false})}>Rejeitar</button></>}</div>)}
    {write&&current.state==="DRAFT"&&<><label>Tipo<select value={kind} onChange={e=>setKind(e.target.value)}>{kinds.map(([k,l])=><option key={k} value={k}>{l}</option>)}</select></label><label>Arquivo<input type="file" accept=".pdf,.png,.jpg,.jpeg" onChange={e=>setFile(e.target.files?.[0]||null)}/></label><button disabled={busy||!file} onClick={()=>void run(()=>upload(false))}>Enviar documento</button><button disabled={busy} onClick={()=>void run(()=>upload(true))}>Enviar fixture de laboratório</button><button className="primary" disabled={busy} onClick={()=>void action("submit")}>Enviar para revisão</button></>}
    <h3>Parceiros e aprovação</h3><pre>{JSON.stringify(current.partners,null,2)}</pre>
    {approve&&current.state==="DOCUMENT_REVIEW"&&<><button disabled={busy} onClick={()=>void action("partners")}>Consultar parceiros em mock</button><button disabled={busy} onClick={()=>void action("decide",{approve:true})}>Aprovar cadastro sandbox</button><button disabled={busy} onClick={()=>void action("decide",{approve:false})}>Devolver para ajustes</button></>}
    {write&&current.state==="CADASTRAL_SANDBOX_APPROVED"&&<button disabled={busy} onClick={()=>void action("provision")}>Provisionar Accounts e loja demo</button>}
    {current.accountsReceipt&&<p>Confirmação Accounts: {current.accountsReceipt}</p>}
    {current.demoId&&<p><a href={"http://localhost:8099/?demo="+current.demoId} target="_blank" rel="noreferrer">Abrir loja demo local</a></p>}
    {write&&current.state==="SANDBOX_READY"&&<button disabled={busy} onClick={()=>void action("test")}>Executar testes de integração</button>}
    {Object.keys(current.tests).length>0&&<><h3>Evidências</h3><pre>{JSON.stringify(current.tests,null,2)}</pre></>}
    {approve&&current.state==="TECHNICAL_REVIEW"&&<button disabled={busy} onClick={()=>void action("technical")}>Aprovar tecnicamente (sandbox)</button>}
    <h3>Auditoria</h3>{audit.map((entry,i)=><p key={i}>{String(entry.event)} · {String(entry.actor)} · {String(entry.created_at)}</p>)}
    </section>}
  </main>;
}
