export type Payment = {
  id: string;
  customer: string;
  email: string;
  amount: number;
  method: string;
  provider: string;
  status: string;
  channel: string;
  day: number;
};
const names = [
  "Marina Costa",
  "Lucas Oliveira",
  "Beatriz Lima",
  "Rafael Santos",
  "Camila Rocha",
  "Pedro Almeida",
  "Ana Ferreira",
  "Bruno Martins",
];
export const payments: Payment[] = Array.from({ length: 84 }, (_, index) => ({
  id: `demo_${10480 + index}`,
  customer: names[index % names.length],
  email: `cliente${index + 1}@example.com`,
  amount: 8900 + ((index * 13937) % 220000),
  method: ["Cartão", "Pix", "Cartão", "Boleto"][index % 4],
  provider: ["Cielo", "PagBank", "Rede", "Getnet"][index % 4],
  status:
    index % 13 === 0
      ? "Em análise"
      : index % 11 === 0
        ? "Recusado"
        : index % 17 === 0
          ? "Estornado"
          : "Pago",
  channel: index % 3 ? "API REST" : "Link",
  day: index % 30,
}));
export const catalog = {
  customers: {
    title: "Clientes",
    subtitle: "O relacionamento começa em cada pagamento.",
    singular: "cliente",
    columns: ["Nome", "E-mail", "Pedidos", "Situação"],
    rows: names.map((name, i) => [
      name,
      `cliente${i + 1}@example.com`,
      String(12 + i * 3),
      "Ativo",
    ]),
  },
  recipients: {
    title: "Recebedores",
    subtitle: "Centralize os participantes do seu marketplace.",
    singular: "recebedor",
    columns: ["Nome", "Identificador", "Modelo", "Situação"],
    rows: [
      ["Loja Aurora", "demo_rec_001", "Pessoa jurídica", "Ativo"],
      ["Estúdio Horizonte", "demo_rec_002", "Pessoa jurídica", "Em análise"],
      ["Include Labs", "demo_rec_003", "Marketplace", "Ativo"],
    ],
  },
  subscriptions: {
    title: "Assinaturas",
    subtitle: "Uma visão da sua receita recorrente.",
    singular: "assinatura",
    columns: ["Cliente", "Plano", "Periodicidade", "Situação"],
    rows: [
      ["Marina Costa", "Essencial · R$ 99", "Mensal", "Ativo"],
      ["Lucas Oliveira", "Profissional · R$ 249", "Mensal", "Ativo"],
      ["Camila Rocha", "Essencial · R$ 99", "Mensal", "Pendente"],
    ],
  },
  disputes: {
    title: "Contestações",
    subtitle: "Organize evidências e acompanhe cada contestação.",
    singular: "contestação",
    columns: ["Transação", "Motivo", "Prazo de resposta", "Situação"],
    rows: [
      ["demo_10491", "Compra não reconhecida", "Em 5 dias", "Em análise"],
      ["demo_10508", "Produto não recebido", "Em 8 dias", "Pendente"],
    ],
  },
  team: {
    title: "Equipe e permissões",
    subtitle: "Planeje os acessos da sua operação.",
    singular: "membro",
    columns: ["Nome", "E-mail", "Perfil", "Situação"],
    rows: [
      ["Administrador demo", "admin@example.com", "Administrador", "Ativo"],
      ["Financeiro demo", "financeiro@example.com", "Analista", "Ativo"],
    ],
  },
};
