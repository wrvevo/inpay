# fraud-service

Porta: 8087.

InPay Risk avalia sinais agregados de comportamento, dispositivo, cadastro e chargeback. O score é explicável. Cada grupo de sinais consome uma quantidade fixa de tokens, usada pelos planos para cobrar o custo de IA. O modo nativo pode ser `INPAY_RISK`, `CLEARSALE` ou `SERASA_EXPERIAN`.

Os dois conectores externos respondem por stub neste serviço. A chamada real permanece desligada. Os processos `connector-clearsale` (8128) e `connector-serasa-experian` (8129) expõem o mesmo stub com `X-Mock`.

