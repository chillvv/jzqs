# Triage Labels

The skills speak in terms of five canonical triage roles. This file maps those roles to the actual label strings used in this repo's issue tracker.

| Label in mattpocock/skills | Label in our tracker | Meaning                                  |
| -------------------------- | -------------------- | ---------------------------------------- |
| `needs-triage`             | `needs-triage`       | Maintainer needs to evaluate this issue  |
| `needs-info`               | `needs-info`         | Waiting on reporter for more information |
| `ready-for-agent`          | `ready-for-agent`    | Fully specified, ready for an AFK agent  |
| `ready-for-human`          | `ready-for-human`    | Requires human implementation            |
| `wontfix`                  | `wontfix`            | Will not be actioned                     |

When a skill mentions a role (e.g. "apply the AFK-ready triage label"), use the corresponding label string from this table.

## 本项目扩展状态

本地 markdown tracker 允许两个额外的工程态（mattpocock 规范之外，本项目自有）：

| Label | Meaning |
| ----- | ------- |
| `claimed` | wayfinder 工单已被认领（claim 机制，防止并发会话重复处理） |
| `in-progress` | 普通工单正在实施中 |

`claimed` 与 `resolved` 成对用于 wayfinder；`in-progress` 与 `done` 用于普通工单。
