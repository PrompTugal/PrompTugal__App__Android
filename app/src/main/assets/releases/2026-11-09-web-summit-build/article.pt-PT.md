---
title: "RiskMandate para a Web Summit: ABP em todas as camadas, o mandato desta app, o riskmandate.ai dentro da app, modo de bancada, Novidades"
date: 2026-11-09
summary: Cinco novidades nesta versão, feitas para uma bancada na Web Summit: uma demonstração em seis camadas, uma vista em direto do alcance e da lacuna desta app, o riskmandate.ai dentro da app, o modo de bancada e estes artigos de versão.
features: [site, abpdemo, mandate, releases]
---

# RiskMandate para a Web Summit

Esta versão acrescenta cinco coisas, feitas para usar num tablet numa bancada. Este artigo diz o que cada uma faz e como usá-la. Foi escrito a partir do código da app. O seu autor, um agente de IA, não correu a app num dispositivo: confirme cada passo no seu tablet antes do dia. O texto em português foi escrito por uma IA e deve ser revisto por um falante nativo antes de ser mostrado.

O ecrã inicial tem agora um botão para cada uma de quatro funcionalidades: **RiskMandate**, **ABP em todas as camadas**, **O mandato desta app** e **Novidades**. A quinta, o modo de bancada, não tem botão: é um interruptor escondido (abaixo).

![O ecrã inicial num tablet de dez polegadas na horizontal.](screenshots/home-tablet.png)

## ABP em todas as camadas

Uma demonstração, identificada como tal no ecrã: "Não é um ataque real: nada é enviado e nada está em risco." Percorre uma regra de uma Política de Comportamento de Agentes (ABP) por seis camadas.

O cenário: pede-se a um agente que resuma um documento. Uma linha plantada no documento manda-o enviar o mapa de agentes da empresa para um anfitrião externo. A regra da ABP é "Mapa de agentes para um anfitrião externo: Bloquear".

São sete passos: o cenário e depois as seis camadas, por esta ordem.

1. No navegador.
2. No modelo.
3. Como hook.
4. Como proxy em linha.
5. No harness seguro.
6. Como política do dispositivo.

Cada passo de camada diz o que a camada faz, a regra que dispara e a força dessa camada: Orientação, Branda, Firme, Forte ou Chão. Diz também com franqueza onde uma camada é fraca: uma página pode ser contornada e um modelo pode ser convencido. A nota do harness seguro diz que uma versão inicial corre em Linux para um processo filho e que o lançador que prenderia o próprio agente ainda é um desenho. O texto vem do riskmandate.ai e do sharness.ai.

**Na bancada:** abra ABP em todas as camadas e toque em Reprodução automática. Avança de sete em sete segundos e volta ao início depois do último passo. Tocar em Seguinte ou Anterior pára a reprodução automática.

![Passo 1 de 7: o cenário.](screenshots/abp-1-scenario.png)

![Passo 2 de 7: a camada do navegador, só como orientação.](screenshots/abp-2-browser.png)

![Passo 5 de 7: o proxy em linha trava o pedido.](screenshots/abp-5-proxy.png)

![O mesmo passo no tema escuro.](screenshots/abp-5-proxy-dark.png)

![Passo 7 de 7: a política do dispositivo, o chão em que as outras camadas assentam.](screenshots/abp-7-device.png)

## O mandato desta app

O riskmandate.ai define alcance, mandato e lacuna, e este ecrã aplica as três palavras a esta app, neste dispositivo, agora. O alcance é lido do Android quando abre o ecrã. A lacuna é calculada a partir do alcance e do mandato, nunca escrita à mão.

- **Alcance:** as permissões que a app pede e se cada uma foi concedida, os componentes que outras apps podem iniciar, se o tráfego http sem cifra é permitido, que armazenamento a app usa e a gestão do dispositivo.
- **Mandato:** o que a política da app permite: apenas https, porta 443, em riskmandate.ai e www.riskmandate.ai. A lista de permissões é testada em direto com endereços de exemplo, cada um mostrado como permitido ou recusado. Lista também a configuração gerida que a app lê e a razão de cada parte do alcance que permite.
- **Lacuna:** ou "nenhuma", ou uma lista do que a app alcança e o mandato não precisa.

As definições no ecrã são citações, em inglês, do riskmandate.ai, lido a 9 de novembro de 2026. Aqui o "agente" é esta app. O ecrã mostra uma linha de resumo com os números do seu dispositivo; são esses que contam, não um número deste artigo.

## O riskmandate.ai dentro da app

O botão RiskMandate abre as páginas do riskmandate.ai dentro da app, com separadores nativos: Início, O que é uma ABP, Como funciona, Preços, SHarness e Sobre. A vista dentro da app está limitada pela mesma verificação de endereços do resto da app: só abre páginas https em riskmandate.ai e www.riskmandate.ai.

Para uma bancada com Wi-Fi fraca, a app tem uma cópia offline das páginas, lidas a 9 de novembro de 2026. Mostra uma faixa a dizê-lo, com um botão Tentar online. Uma página que não está na cópia diz-o e pede ligação. O ecrã avisa ainda que o riskmandate.ai tem a sua própria política de privacidade e pode definir os seus próprios cookies, enquanto esta app não recolhe nada.

As capturas de ecrã abaixo mostram a moldura da app em volta da página: os separadores, a faixa offline e a linha de privacidade. Não mostram a página em si.

![O ecrã RiskMandate num telemóvel: separadores, faixa offline e linha de privacidade, sem a página.](screenshots/site-phone.png)

![O mesmo ecrã num tablet de dez polegadas na horizontal.](screenshots/site-tablet.png)

As palavras e os preços dessas páginas são do riskmandate.ai, não desta app. Se o site mudou desde 9 de novembro, a cópia offline fica desatualizada até à versão seguinte.

## Modo de bancada

O modo de bancada serve para um tablet deixado a funcionar numa bancada. Está **desativado por defeito** e não tem botão. Quando está ativo:

- o ecrã fica ligado;
- o ecrã fica fixado, se o Android o permitir, o que dificulta a saída da app a um visitante. Dissuade, mas não bloqueia o tablet, porque o gesto do próprio Android para libertar o ecrã continua a funcionar (abaixo);
- passado um número de minutos sem atividade, a app volta ao ecrã inicial. O valor por defeito é 3 minutos e pode definir de 1 a 30.

**Para ativar ou desativar:** no ecrã inicial, **prima e mantenha o canto inferior direito** (um quadrado de cerca de 48 píxeis independentes da densidade, dentro da área segura). Abre o painel de definições. Use o interruptor "Modo de bancada ativo" e Menos e Mais para os minutos. Quando está ativo, vê-se um ponto discreto nesse canto.

O Android pergunta uma vez se deve fixar o ecrã. Se recusar, ou se a fixação de ecrã estiver desativada em Definições, Segurança, o painel diz que o ecrã não está fixado, e manter o ecrã ligado e o regresso ao início continuam a funcionar. Para libertar o ecrã com o gesto do próprio Android: com navegação por três botões, mantenha premidos Voltar e Vista geral em simultâneo; com navegação por gestos, deslize para cima e mantenha. Se for pedido, introduza o PIN do ecrã de bloqueio. Planeie como tirar um tablet da fixação antes do dia.

As definições ficam só no dispositivo: modo de bancada ativo ou não, e os minutos.

Enquanto o ecrã está fixado, o botão Abrir riskmandate.ai do ecrã inicial não abre nenhum navegador. Mostra a mensagem "As páginas web não são abertas num navegador enquanto a aplicação estiver fixada." O botão RiskMandate, que mantém o riskmandate.ai dentro da app, continua a funcionar; as ligações que saem do riskmandate.ai não são abertas enquanto o ecrã estiver fixado.

## Novidades

Esta secção. Cada versão tem um artigo como este, incluído na app, por isso lê-se offline. Abra Novidades no ecrã inicial e escolha uma versão. Num tablet na horizontal, a lista e o artigo ficam lado a lado. Os artigos em português europeu aparecem nos dispositivos configurados em português.

![A lista de versões num telemóvel.](screenshots/whatsnew-list.png)

![Um artigo de versão ao lado da lista, num tablet na horizontal.](screenshots/whatsnew-reader.png)

## O que não está aqui

- Sem contas, análises ou publicidade. A app continua a pedir só duas permissões: internet e estado da rede.
- O modo de bancada, O mandato desta app e a vista RiskMandate dentro da app ainda não têm capturas de ecrã neste artigo; a build só tira capturas de ecrãs com um teste de capturas.
- Nada aqui é uma afirmação sobre o RiskMandate além do que o riskmandate.ai diz.

O número da versão está em baixo no ecrã inicial. Indique-o ao comunicar um problema.
