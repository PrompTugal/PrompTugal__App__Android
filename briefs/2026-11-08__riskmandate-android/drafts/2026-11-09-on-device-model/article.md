---
title: "A language model on the tablet: first chat, model setup and the Workbench"
date: 2026-11-09
summary: The app can now download a language model once and chat with it on the device. This build is a preview for the team's own tablets. It also describes the Workbench, an experiment screen for the model that is still being built.
features: [chat, models, lab, workbench]
---

# A language model on the tablet

On 9 November the app ran a language model on a tablet for the first time. This article says what exists, how to find it, what the model costs in time and space, and what is not finished. It is written from the source code, the project's briefs and the conductor's notes. Its author, an AI agent, has not run any of it on a device.

## Where to find it

These screens are a **preview**. They are switched off for everyone and are meant for the team's own tablets: open **Diagnostics**, then **Preview**. The app says there: "These screens are being tested on this device. They are not switched on for everyone and may not work yet." They are hidden in stand mode.

## Model setup

The first step is a one-off setup screen. You choose a model from a short list, see its size, download it once and the app checks it.

- **Models:** Gemma 4 E2B is the default (about 2.6 GB). Gemma 4 E4B is the optional larger one (about 3.7 GB). The weight licence of each is recorded in the source as the model card states it, and for both it reads Apache 2.0.
- **One source, checked files:** the download comes from huggingface.co and its download servers (hf.co), from a list of exact addresses fixed in the code. The app follows a few redirects, only to https addresses on Hugging Face's own hosts. Each file's size and SHA-256 hash are fixed in the code, and a file that does not match is deleted and never opened.
- **Resumable:** a partly downloaded file is kept and the download resumes. There is a progress bar, a check of free storage first, and a Delete model button.
- **A fallback for bad Wi-Fi:** the same file can be put in the app's folder by hand with a computer (adb), and gets the same checks.
- **What the app says:** "Downloads a model file once from huggingface.co (and its download servers, hf.co). Nothing you type leaves the device."

The download is the one time the app contacts Hugging Face and its download servers, and only when you tap Download. After it, the model runs on the device and works offline.

## Chat

A chat with the local model, to show it is a real language model: streamed answers, a stop button, a new chat, a badge that reads "Runs on this device", and the speed of each answer.

- **First run, build 0.1.78 on a Lenovo Idea Tab Plus (12 GB):** Gemma 4 E2B downloaded on the tablet and Chat answered on the device at about 5.4 to 5.5 tokens per second as the app reported it. The two timings given with it, 68 tokens in 13.6 seconds and 140 tokens in 26.5 seconds, work out to about 5.0 and 5.3 tokens per second over the whole answer. That is one run on one tablet, from the project's notes; the device bench will give better numbers.
- **What that means:** a short answer takes several seconds and a long one about half a minute. It is usable for a demonstration, and it is slow.
- **Still a preview:** the first run turned up a few things to fix, and they have been fixed since. The chat may still have rough edges.

The three pictures below are drawn by the screenshot tests with a **fake model that returns canned text**, so they show the screen and not the model.

![Chat before a model is installed (a render with a fake model).](screenshots/chat-no-model.png)

![An empty chat with preset prompts (a render with a fake model).](screenshots/chat-empty.png)

![A streamed answer (a render with a fake model and canned text).](screenshots/chat-answer.png)

## The runtime

The model runs through LiteRT-LM, Google AI Edge's runtime for on-device models (Apache 2.0), version 0.18.1. The model files are the ones that Google AI Edge Gallery's allow-list (version 1.0.23) offers. The plan is to use the GPU where there is one and fall back to the CPU, and the Workbench can vary the backend.

## ABP Lab

The Lab runs the same input down two paths: raw, and through an Agent Behaviour Policy that checks every tool call before it runs. All tools in it are fakes that record calls and touch nothing real, and the planted instructions are fixed text shown on screen. It is in preview like the rest. It is a demonstration of how a policy catches mistakes a small model really makes. It is not a claim about RiskMandate beyond what riskmandate.ai says.

## The Workbench (in progress)

The Workbench is a control centre for experiments with the on-device model, asked for by Dinis Cruz on 9 November after the first chat. It is **not yet used on the tablet**, so treat this as a plan with some of it built:

- **Run:** set the model, backend, system prompt, user prompt and sampling settings, optionally offer fake tools, and watch the answer stream with live statistics: load time, tokens in and out, time to the first token, prefill and decode speed, the stop reason, and the tool calls proposed. Each figure is marked as read from the runtime or measured in the app.
- **Experiments:** a run log kept on the device, and sweeps or A/B comparisons over one setting at a time, with a table of the mean and spread of each number. It stops at set limits: 100 runs or 30 minutes, 15% battery when not charging, severe heat, or three failed runs in a row.
- **Tool tests:** a catalogue of small, deterministic cases that check how a model calls tools. A planned part, not yet seen.

The Run and Experiments tabs are merged but not yet in the app's feature list.

## What stays on the device

The app collects no personal data and sends nothing to its makers. Your chat, if you use it, is kept on the device only, in storage that Android never backs up, with a Clear history button; model files are kept the same way and the Delete model button removes them. The Workbench's run log is kept on the device too. The privacy policy lists every stored item. The app still asks for only two permissions: internet and network state.

The build number is at the bottom of the home screen. Quote it when you report a problem.
