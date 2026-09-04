# 데이터팩 장비 공정

[English](MACHINE_PROCESSES.md) | 한국어 · [프로젝트 README](../README.kr.md)

Siliconic 장비 공정은 `siliconic:machine_process` 레시피를 사용해 추가할 수 있습니다. 데이터팩에서 내장 공정과 동일한 레시피 ID를 사용하면 해당 공정을 덮어쓸 수 있습니다.

```json
{
  "type": "siliconic:machine_process",
  "machine": "chemical_recycler",
  "shaped": true,
  "ticks": 220,
  "energy_per_tick": 35,
  "inputs": [
    {
      "slot": 0,
      "ingredient": {"item": "siliconic:distillation_residue"},
      "count": 1,
      "use": "consume"
    }
  ],
  "result": {"item": "siliconic:crude_trichlorosilane", "count": 1},
  "byproducts": []
}
```

`machine` 필드는 `silicon_arc_furnace`, `chlorination_reactor`, `distillation_tower`, `siemens_reactor`, `chemical_recycler`, `wafer_fabricator`, `gate_fabricator` 중 하나여야 합니다.

입력의 `use` 필드는 다음 값을 지원합니다.

- `consume`: 배치가 완료되면 설정된 수량만큼 아이템을 제거합니다.
- `damage`: 내구도가 있는 아이템 하나가 필요하며, 배치가 완료되면 `count`만큼 내구도 손상을 적용합니다.
- `catalyst`: 아이템을 소모하지 않고 요구 조건으로만 사용합니다.

비정형 공정에서는 `shaped`를 `false`로 설정하고 모든 입력 슬롯에 `-1`을 사용합니다. 일치 검사기는 서로 겹치는 재료 조건도 각각 별개의 가용 아이템에 할당하므로, 하나의 스택으로 여러 요구 조건을 동시에 충족할 수 없습니다.

정형 공정의 슬롯은 장비 입력 영역을 기준으로 0부터 시작하는 인덱스입니다. 산업용 처리 장비는 `0`부터 `2`까지의 슬롯을 사용하며, 웨이퍼 및 게이트 제조기는 `0`부터 `8`까지의 슬롯을 사용합니다. 중복되거나 범위를 벗어난 정형 슬롯은 데이터팩을 불러올 때 거부됩니다. `ticks`, `energy_per_tick`, 입력 수량과 출력 수량은 양의 정수여야 합니다. 출력 수량은 해당 출력 아이템의 최대 스택 크기를 초과할 수 없습니다.

## 재처리 레시피

재처리기는 `siliconic:reprocessing` 레시피 유형을 사용합니다.

```json
{
  "type": "siliconic:reprocessing",
  "input": {"item": "siliconic:silicon_slag", "count": 4},
  "outputs": [
    {"item": "minecraft:quartz"},
    {"item": "minecraft:charcoal"}
  ],
  "ticks": 240,
  "energy_per_tick": 40
}
```

`input` 객체에는 아이템 하나와 양의 수량을 지정할 수 있습니다. `outputs`에는 비어 있지 않은 아이템 스택이 하나 이상 포함되어야 하며, 각 출력 수량은 해당 아이템의 최대 스택 크기를 초과할 수 없습니다. `ticks`와 `energy_per_tick`도 양의 정수여야 합니다.
