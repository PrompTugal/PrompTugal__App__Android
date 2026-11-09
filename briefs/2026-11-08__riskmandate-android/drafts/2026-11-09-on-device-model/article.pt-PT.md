---
title: "Um modelo de linguagem no tablet: primeiro chat, configuração do modelo e a Workbench"
date: 2026-11-09
summary: A app já consegue descarregar um modelo de linguagem uma vez e conversar com ele no dispositivo. Esta versão é uma pré-visualização para os tablets da equipa. Descreve também a Workbench, um ecrã de experiências com o modelo que ainda está a ser construído.
features: [chat, models, lab, workbench]
---

# Um modelo de linguagem no tablet

A 9 de novembro a app correu pela primeira vez um modelo de linguagem num tablet. Este artigo diz o que existe, como o encontrar, o que o modelo custa em tempo e espaço e o que não está acabado. Foi escrito a partir do código, dos briefs do projeto e das notas do condutor. O seu autor, um agente de IA, não correu nada disto num dispositivo. O texto em português foi escrito por uma IA e deve ser revisto por um falante nativo antes de ser mostrado.

## Onde encontrar

Estes ecrãs são uma **pré-visualização**. Estão desativados para toda a gente e destinam-se aos tablets da própria equipa: abra **Diagnóstico** e depois **Pré-visualização**. A app diz aí: "Estes ecrãs estão a ser testados neste dispositivo. Não estão ativos para toda a gente e podem ainda não funcionar." Ficam escondidos no modo de bancada.

## Configurar o modelo

O primeiro passo é um ecrã de configuração, feito uma só vez. Escolhe um modelo de uma lista curta, vê o tamanho, descarrega-o uma vez e a app verifica-o.

- **Modelos:** o Gemma 4 E2B é o predefinido (cerca de 2,6 GB). O Gemma 4 E4B é o maior, opcional (cerca de 3,7 GB). A licença dos pesos de cada um está registada no código como o cartão do modelo a indica, e nos dois diz Apache 2.0.
- **Um servidor, ficheiros verificados:** o descarregamento vem de huggingface.co, de uma lista de endereços exatos fixada no código. A app segue poucos redirecionamentos, só para endereços https nos servidores do próprio Hugging Face. O tamanho e o SHA-256 de cada ficheiro estão fixados no código, e um ficheiro que não coincida é apagado e nunca aberto.
- **Retomável:** um ficheiro descarregado a meio é mantido e o descarregamento continua. Há uma barra de progresso, uma verificação prévia do espaço livre e um botão para apagar o modelo.
- **Alternativa para Wi-Fi fraco:** o mesmo ficheiro pode ser colocado à mão na pasta da app com um computador (adb), e passa pelas mesmas verificações.
- **O que a app diz:** "Descarrega um ficheiro de modelo uma vez de huggingface.co. Nada do que escreve sai do dispositivo."

O descarregamento é a única vez em que a app contacta o Hugging Face, e só quando toca em Descarregar. Depois, o modelo corre no dispositivo e funciona offline.

## Chat

Um chat com o modelo local, para mostrar que é um modelo de linguagem a sério: respostas em fluxo, um botão de parar, uma nova conversa, uma etiqueta a dizer que o modelo está no dispositivo e offline, e a velocidade de cada resposta.

- **Primeira execução, versão 0.1.78 num Lenovo Idea Tab Plus (12 GB):** o Gemma 4 E2B foi descarregado no tablet e o Chat respondeu no dispositivo a cerca de 5,4 a 5,5 tokens por segundo: 68 tokens em 13,6 segundos e 140 tokens em 26,5 segundos. É uma execução num só tablet, a partir das notas do projeto; o teste de desempenho do dispositivo dará números melhores.
- **O que isso significa:** uma resposta curta demora alguns segundos e uma longa cerca de meio minuto. Serve para uma demonstração, e é lento.
- **Arestas por limar:** a primeira execução revelou coisas a corrigir: uma linha curta fixa do riskmandate.ai no início de cada pedido, a redação da etiqueta de dispositivo, marcas de formatação perdidas nas respostas e uma verificação das definições de amostragem. Trate o chat como inacabado.

As três imagens abaixo são desenhadas pelos testes de capturas com um **modelo de teste que devolve texto fixo**, por isso mostram o ecrã e não o modelo.

![O chat antes de haver um modelo instalado (uma representação com um modelo de teste).](screenshots/chat-no-model.png)

![Um chat vazio com pedidos predefinidos (uma representação com um modelo de teste).](screenshots/chat-empty.png)

![Uma resposta em fluxo (uma representação com um modelo de teste e texto fixo).](screenshots/chat-answer.png)

## O motor

O modelo corre através do LiteRT-LM, o motor da Google AI Edge para modelos no dispositivo (Apache 2.0), versão 0.18.1. É a versão que a própria app Google AI Edge Gallery traz com os mesmos ficheiros Gemma 4. O plano é usar o GPU onde existir e recorrer ao CPU, e a Workbench pode variar o motor.

## Laboratório ABP

O Laboratório corre a mesma entrada por dois caminhos: em bruto, e através de uma Política de Comportamento de Agentes que verifica cada chamada de ferramenta antes de ela correr. Todas as ferramentas são falsas, registam chamadas e não tocam em nada real, e as instruções plantadas são texto fixo mostrado no ecrã. Está em pré-visualização como o resto. É uma demonstração de como uma política apanha erros que um modelo pequeno comete de facto. Não é uma afirmação sobre o RiskMandate além do que o riskmandate.ai diz.

## A Workbench (em curso)

A Workbench é um centro de controlo para experiências com o modelo no dispositivo, pedido por Dinis Cruz a 9 de novembro depois do primeiro chat. **Ainda não foi usada no tablet**, por isso trate isto como um plano com parte já construída:

- **Executar:** defina o modelo, o motor, o prompt do sistema, o prompt do utilizador e as definições de amostragem, ofereça opcionalmente ferramentas falsas e veja a resposta em fluxo com estatísticas em direto: tempo de carregamento, tokens à entrada e à saída, tempo até ao primeiro token, velocidade de preenchimento e de descodificação, o motivo de paragem e as chamadas de ferramentas propostas. Cada valor indica se foi lido do motor ou medido na app.
- **Experiências:** um registo de execuções guardado no dispositivo, e varrimentos ou comparações A/B sobre uma definição de cada vez, com uma tabela da média e da dispersão de cada número. Pára em limites fixos: 100 execuções ou 30 minutos, 15% de bateria quando não está a carregar, calor severo ou três execuções falhadas seguidas.
- **Testes de ferramentas:** um catálogo de casos pequenos e determinísticos que verificam como um modelo chama ferramentas. Uma parte planeada, ainda não vista.

Os separadores Executar e Experiências estão integrados mas ainda não na lista de funcionalidades da app.

## O que fica no dispositivo

A app não recolhe dados pessoais e não envia nada aos seus autores. A sua conversa, se usar o chat, fica só no dispositivo, em armazenamento que o Android nunca copia, com um botão Limpar histórico; os ficheiros de modelo ficam do mesmo modo e o botão Apagar remove-os. O registo de execuções da Workbench também fica no dispositivo. A política de privacidade lista cada item guardado. A app continua a pedir só duas permissões: internet e estado da rede.

O número da versão está em baixo no ecrã inicial. Indique-o ao comunicar um problema.
