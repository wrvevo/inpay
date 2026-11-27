# InPay · Acessos DEV, STG e PRD demo

| Ambiente | Painel HTTPS | Branch de origem | Banco de dados AWS | Usuário e senha PostgreSQL |
| --- | --- | --- | --- | --- |
| DEV | https://dev.d2ycazp3c24sbc.amplifyapp.com | `develop` | Pendente de provisionamento | Ainda não atribuídos |
| STG | https://stg.d2ycazp3c24sbc.amplifyapp.com | `staging` | Pendente de provisionamento | Ainda não atribuídos |
| PRD demo | https://prd-demo.d2ycazp3c24sbc.amplifyapp.com | `main` | Pendente de provisionamento | Ainda não atribuídos |

Os painéis hospedados no Amplify em `us-east-2` usam o acesso demonstrativo predefinido na página de entrada. Escolha **Gateway** para abrir o painel ou **Homologação** para a jornada demonstrativa. O formulário apresenta o usuário e a senha de demonstração. Esse acesso utiliza dados fictícios e é próprio da interface.

## Importar no DBeaver

1. Abra **File → Import → Third Party Configuration → Custom**.
2. Selecione o driver **PostgreSQL** e o formato XML.
3. Importe o arquivo do ambiente desejado. Cada arquivo tem 24 perfis, um por banco definido em [init-databases.sql](../local/init-databases.sql).
4. Quando o banco estiver disponível, ajuste o host, valide a CA do servidor e informe o usuário e a senha autorizados para o ambiente no DBeaver. Os perfis preveem porta `5432` e TLS com verificação do nome do servidor.

Os hosts em `example.invalid` identificam perfis que ainda aguardam um endpoint. As contas de aplicação definidas no SQL local servem ao Compose de desenvolvimento. Os acessos individuais do DBeaver serão emitidos na implantação do banco e devem ser recuperados pelo canal autorizado do AWS Secrets Manager.
