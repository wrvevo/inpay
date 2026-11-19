import { useEffect, useRef, type ReactNode } from "react";
import { X, ArrowUpRight, Inbox } from "lucide-react";

export function Badge({ children }: { children: ReactNode }) {
  const value = String(children);
  const tone = /Pago|Ativo|ACCEPT|Disponível|AUTHORIZED/.test(value)
    ? "good"
    : /Recus|REJECT|Estorn/.test(value)
      ? "bad"
      : "neutral";
  return (
    <span className={`badge ${tone}`}>
      <i />
      {children}
    </span>
  );
}
export function Heading({
  title,
  subtitle,
  children,
}: {
  title: string;
  subtitle: string;
  children?: ReactNode;
}) {
  return (
    <div className="page-heading">
      <div>
        <h1>{title}</h1>
        <p>{subtitle}</p>
      </div>
      <div className="actions">{children}</div>
    </div>
  );
}
export function Metric({
  label,
  value,
  note,
}: {
  label: string;
  value: string;
  note: string;
}) {
  return (
    <article className="metric">
      <div>
        {label}
        <ArrowUpRight size={17} />
      </div>
      <strong>{value}</strong>
      <small>{note}</small>
    </article>
  );
}
export function Empty({
  title = "Nenhum registro encontrado",
  children,
}: {
  title?: string;
  children?: ReactNode;
}) {
  return (
    <div className="empty">
      <Inbox size={32} />
      <h3>{title}</h3>
      <p>{children ?? "Ajuste os filtros para continuar."}</p>
    </div>
  );
}
export function Notice({ children }: { children: ReactNode }) {
  return <div className="notice">{children}</div>;
}

/** Native modal supplies focus containment; restore focus to the invoker on unmount. */
export function Modal({
  title,
  onClose,
  children,
}: {
  title: string;
  onClose: () => void;
  children: ReactNode;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const previous = document.activeElement as HTMLElement;
    ref.current?.showModal();
    return () => previous?.focus();
  }, []);
  return (
    <dialog ref={ref} onCancel={onClose} aria-labelledby="dialog-title">
      <div className="modal-heading">
        <h2 id="dialog-title">{title}</h2>
        <button className="icon-button" onClick={onClose} aria-label="Fechar">
          <X />
        </button>
      </div>
      {children}
    </dialog>
  );
}
