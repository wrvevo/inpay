# painel-gerencial

Porta 8130. Sistema de identidade do gateway e de homologação das lojas.

No perfil local a senha dos usuários de exemplo é `inpay@123`. O login devolve um token HMAC aceito pelas APIs locais. Fora do perfil local, as APIs exigem o access token do Cognito.

Usuários do painel: `admin@inpay.local`, `manager@inpay.local`, `viewer@inpay.local`.

Usuários da homologação: `pdv@inpay.local`, `backoffice@inpay.local`, `homologacao@inpay.local`.

O estado fica na memória do processo e volta aos exemplos a cada início. A base `painel_gerencial` existe para conexão e para o próximo passo de persistência.
