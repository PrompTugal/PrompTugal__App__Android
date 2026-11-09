package ai.riskmandate.app.features.lab

import ai.riskmandate.app.experiments.FakeTools

/** The scenario's fake tools: what the Lab runs a valid call on. A thin wrapper over the shared recorder. */
fun FakeTools.execute(scenario: Scenario, name: String, args: Map<String, String>): Any? = execute(scenario.fakeTools, scenario.graph, name, args)
