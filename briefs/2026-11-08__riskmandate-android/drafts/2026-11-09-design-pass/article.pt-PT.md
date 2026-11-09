---
title: "Um novo aspeto: o desenho PrompTugal, em verde RiskMandate"
date: 2026-11-09
summary: A app ganha o aspeto das apps PrompTugal: papel quente, cartões, títulos com serifa, um acento verde e um modo escuro azulado, uma marca RM redesenhada e uma nova moldura com barra lateral e página Agora.
features: [releases, mandate, site, abpdemo]
---

# Um novo aspeto

Esta versão muda o aspeto e a disposição da app. Nada nela acrescenta uma nova capacidade do RiskMandate, e nada aqui diz sobre o RiskMandate mais do que o riskmandate.ai diz. Este artigo foi escrito a partir dos documentos de desenho e do código. O seu autor, um agente de IA, não correu a app num dispositivo: confirme cada ponto no seu tablet. O texto em português foi escrito por uma IA e deve ser revisto por um falante nativo antes de ser mostrado.

## O aspeto

A app segue agora o aspeto das apps PrompTugal: papel quente no fundo, cartões com uma linha fina e cantos arredondados, títulos com serifa e uma pequena etiqueta em maiúsculas (kicker) por cima de cada título. O Dinis Cruz escolheu a variante A de duas propostas.

- **Acento:** o verde RiskMandate marca o botão principal, o item selecionado e as etiquetas. O vermelho fica para o que está bloqueado e para erros.
- **Modo escuro:** um tema escuro azulado. Claro e escuro seguem a definição do dispositivo.
- **Contraste:** as notas de desenho registam o contraste medido com a fórmula WCAG para os textos e as cores de acento.

O ecrã inicial como o teste de capturas o desenha, claro e escuro:

![O ecrã inicial num tablet de dez polegadas na horizontal, tema claro.](screenshots/home-tablet-light.png)

![O mesmo ecrã inicial, tema escuro.](screenshots/home-tablet-dark.png)

## A marca RM

O monograma RM foi redesenhado em imagens vetoriais a partir do logótipo fornecido pelo Dinis Cruz: um RM branco com dois cantos em esquadria sobre um quadrado azul-marinho. Foi desenhado à mão a partir de uma imagem de referência pequena, por isso as letras diferem em pormenor do logótipo original. Um vetor próprio de quem desenhou o logótipo substituí-lo-ia. O novo ícone de lançamento e o ecrã de arranque chegam com a nova moldura, abaixo.

## Cada funcionalidade tem um cartão

Cada funcionalidade tem agora um ícone, uma etiqueta e um resumo de uma linha, usados na página inicial e na barra lateral. Por exemplo:

- **RiskMandate:** "riskmandate.ai". As páginas do riskmandate.ai dentro da app, com uma cópia offline para Wi-Fi fraca.
- **ABP em todas as camadas:** "Demonstração". Uma Política de Comportamento de Agentes mantida em seis camadas, não um ataque real.
- **O mandato desta app:** "Ao vivo neste dispositivo". O alcance, o mandato e a lacuna da própria app, lidos do dispositivo quando a abre.
- **Novidades:** "Notas de versão". Um artigo por versão, com capturas de ecrã do que mudou.

## A nova moldura

Esta parte chega com o novo shell da app. Está descrita a partir da especificação de desenho: confirme-a na sua versão.

- **Tablets (na horizontal, ou com 840 dp de largura ou mais):** uma barra lateral permanente à esquerda. Tem o bloco da marca e depois uma linha por funcionalidade, em secções. Pode recolher-se numa faixa estreita de ícones, e a app lembra-se disso no dispositivo.
- **Modo de bancada:** a barra lateral começa recolhida, para o conteúdo ocupar o ecrã. O pessoal pode abri-la.
- **Telemóveis e vertical:** o mesmo painel como gaveta, aberta por um botão de menu com legenda.
- **Agora:** a página inicial passa a ser uma página de cartões: três mosaicos (funcionalidades ativas, o que a app recolhe, o que precisa de atenção) e depois um cartão por funcionalidade.

As imagens abaixo são representações desse desenho, feitas pelos testes de capturas com conteúdo de exemplo. Não são capturas da app a funcionar.

![O desenho da moldura num tablet, tema claro: a barra lateral e a página Agora (uma representação com conteúdo de exemplo).](screenshots/frame-light.png)

![O mesmo desenho, tema escuro (uma representação com conteúdo de exemplo).](screenshots/frame-dark.png)

![A página Agora num telemóvel (uma representação com conteúdo de exemplo).](screenshots/now-phone.png)

## O ecrã do mandato lê mais

O mandato desta app olha para mais do que a app guarda no dispositivo: o armazenamento sem cópia de segurança e os ficheiros no armazenamento partilhado. A lista de razões cobre agora a única preferência de apresentação da moldura (barra lateral recolhida ou não), o número da última nota de atualização mostrada e, nas versões que têm essas funcionalidades, o histórico de conversas e os ficheiros de modelos de linguagem. Cada um é indicado como guardado só neste dispositivo e nunca copiado.

## Os textos de privacidade

A política de privacidade e as respostas de segurança dos dados foram atualizadas para listar tudo o que a app guarda: a preferência de apresentação, o número da última nota de atualização mostrada e, onde as funcionalidades existem, o histórico de conversas e os ficheiros de modelos, com a forma de os apagar. A app continua a não recolher nada e a não enviar nada. Continua a pedir só duas permissões: internet e estado da rede.

O número da versão está em baixo no ecrã inicial. Indique-o ao comunicar um problema.
