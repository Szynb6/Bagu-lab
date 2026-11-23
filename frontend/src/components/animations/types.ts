import type { Component } from 'vue'

export type Tone = 'slate' | 'brand' | 'emerald' | 'amber' | 'red' | 'indigo' | 'purple' | 'cyan'

/**
 * 1. 通用架构/流程/内存流转动画规范（FlowDiagramEngine）
 */
export interface FlowZone {
  id: string
  label: string
  x: number
  y: number
  w: number
  h: number
  tone?: Tone
}

export interface FlowNode {
  id: string
  label: string
  sub?: string
  x: number
  y: number
  w: number
  h: number
  tone?: Tone
}

export interface FlowEdge {
  id: string
  from: string
  to: string
  label?: string
  path?: string
  lx?: number
  ly?: number
  curved?: number
}

export interface FlowStep {
  title: string
  badge: string
  speech: string
  tip: string
  activeNodes?: string[]
  doneNodes?: string[]
  warnNodes?: string[]
  dangerNodes?: string[]
  mutedNodes?: string[]
  activeEdges?: string[]
  nodeText?: Record<string, { label?: string; sub?: string }>
  metrics?: { label: string; value: string; tone?: Tone }[]
}

export interface FlowScenario {
  id: string
  label: string
  zones?: FlowZone[]
  nodes: FlowNode[]
  edges: FlowEdge[]
  steps: FlowStep[]
}

export interface FlowSpec {
  kind: 'flow'
  title: string
  subtitle: string
  viewBox?: string
  scenarios: FlowScenario[]
}

/**
 * 2. 通用时序报文/双线程交互动画规范（SequenceDiagramEngine）
 */
export interface SequenceActor {
  id: string
  label: string
  sub?: string
  tone?: Tone
}

export interface SequenceMessage {
  from: string
  to: string
  label: string
  tone?: Tone
  dashed?: boolean
  lost?: boolean
}

export interface SequenceStep {
  title: string
  badge: string
  speech: string
  tip: string
  message?: SequenceMessage
  actorStates?: Record<string, { text: string; tone?: Tone }>
  callout?: { actor: string; text: string; tone?: Tone }
}

export interface SequenceScenario {
  id: string
  label: string
  actors: SequenceActor[]
  steps: SequenceStep[]
}

export interface SequenceSpec {
  kind: 'sequence'
  title: string
  subtitle: string
  scenarios: SequenceScenario[]
}

/**
 * 3. 通用状态机/图遍历动画规范（StateMachineEngine）
 */
export interface StateNode {
  id: string
  label: string
  sub?: string
  x: number
  y: number
  r?: number
  w?: number
  h?: number
  tone?: Tone
}

export interface StateTransition {
  id: string
  from: string
  to: string
  label: string
  curved?: number
  action?: string
}

export interface StateStep {
  title: string
  badge: string
  speech: string
  tip: string
  currentNodes: string[]
  visitedNodes?: string[]
  dangerNodes?: string[]
  activeTransitions?: string[]
  nodeTags?: Record<string, string>
}

export interface StateSpec {
  kind: 'state'
  title: string
  subtitle: string
  viewBox?: string
  nodes: StateNode[]
  transitions: StateTransition[]
  steps: StateStep[]
  interactive?: boolean
  initialNode?: string
}

export type DeclarativeSpec = FlowSpec | SequenceSpec | StateSpec

export interface AnimationEntry {
  key: string
  title: string
  subtitle: string
  component?: Component
  loadSpec?: () => Promise<DeclarativeSpec>
}
