# 데이터팩 장비 공정

[English](MACHINE_PROCESSES.md) | 한국어 · [프로젝트 README](../README.kr.md)

Siliconic 장비 공정은 `siliconic:machine_process` 레시피를 사용해 추가할 수 있습니다. 데이터팩에서 내장 공정과 동일한 레시피 ID를 사용하면 해당 공정을 덮어쓸 수 있습니다.

## 파일 위치와 다시 불러오기

데이터팩의 `data/<namespace>/recipes/<path>.json`에 공정 파일을 넣습니다. 예를 들어 `data/example/recipes/residue_recovery.json`의 레시피 ID는 `example:residue_recovery`입니다. 기본 잔류물 공정을 바꾸려면 ID가 `siliconic:chemical_recycler/distillation_residue`인 `data/siliconic/recipes/chemical_recycler/distillation_residue.json`을 사용합니다.

데이터팩은 월드의 `datapacks` 폴더에 설치합니다. 데이터팩 루트에는 `pack.mcmeta`가 있어야 하며, Minecraft 1.20.1의 팩 형식 번호는 15입니다.

```json
{
  "pack": {
    "pack_format": 15,
    "description": "Custom Siliconic processes"
  }
}
```

서버 데이터를 바꾼 뒤 `/reload`를 실행하고 서버 로그에서 레시피 로딩 오류를 확인합니다.

## 장비 공정 예제

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

## 지원 장비

`machine` 필드에는 네임스페이스 없이 다음 이름 중 하나를 사용합니다.

| 장비 | 입력 슬롯 | 추가 운전 조건 |
| --- | --- | --- |
| `silicon_arc_furnace` | `0`~`2` | 단계에 따라 전력 소비량이 달라집니다. |
| `chlorination_reactor` | `0`~`2` | 마그마 열과 압력 관리가 필요합니다. |
| `distillation_tower` | `0`~`2` | 마그마 열이 필요하며 모드와 안정도가 처리 시간에 영향을 줍니다. |
| `siemens_reactor` | `0`~`2` | 마그마 열이 필요하며 단계에 따라 전력 소비량이 달라집니다. |
| `chemical_recycler` | `0`~`2` | 마그마 열이 필요하며 출력 종류별 슬롯이 구분됩니다. |
| `wafer_fabricator` | `0`~`8` | 컨디셔너가 감시하는 밀폐 클린룸이 필요합니다. |
| `gate_fabricator` | `0`~`8` | 컨디셔너가 감시하는 밀폐 클린룸이 필요합니다. |

모든 장비에는 전력도 필요합니다. 산업용 장비의 입력 슬롯은 왼쪽부터 순서대로 번호를 매깁니다. 제조기의 입력 슬롯은 3×3 격자에서 왼쪽에서 오른쪽, 위에서 아래 순서로 번호를 매깁니다. 이 번호는 입력 영역을 기준으로 하며 열원과 출력 슬롯은 포함하지 않습니다.

## 필드와 기본값

| 필드 | 의미 |
| --- | --- |
| `type` | 필수 값은 `siliconic:machine_process`입니다. |
| `machine` | 위에 나열한 장비 이름 중 하나가 필요합니다. |
| `shaped` | 입력 위치를 구분할지 지정하며 기본값은 `true`입니다. |
| `ticks` | 기본 처리 시간을 게임 틱 단위의 양의 정수로 지정합니다. 필수 값입니다. |
| `energy_per_tick` | 기본 소비량을 틱당 SE 단위의 양의 정수로 지정합니다. 필수 값입니다. |
| `inputs` | 비어 있지 않은 입력 정의 배열이 필요합니다. |
| `result` | 주 생산물의 `item` ID가 필요하며 `count`의 기본값은 `1`입니다. |
| `byproducts` | `result`와 같은 형식의 부산물 배열이며 기본값은 빈 배열입니다. |

각 입력에는 다음 필드를 사용합니다.

| 필드 | 의미 |
| --- | --- |
| `slot` | 정형 공정은 입력 영역의 슬롯 번호를, 비정형 공정은 `-1`을 사용합니다. 생략하면 정형 공정은 배열의 해당 인덱스, 비정형 공정은 `-1`이 적용됩니다. |
| `ingredient` | `{"item": "minecraft:quartz"}`나 `{"tag": "forge:nuggets/copper"}` 같은 재료 정의가 필요합니다. |
| `count` | 아이템 수량 또는 `damage` 입력의 내구도 소모량을 양의 정수로 지정합니다. 기본값은 `1`입니다. |
| `use` | 입력 처리 방식이며 기본값은 `consume`입니다. |

입력의 `use` 필드는 다음 값을 지원합니다.

- `consume`: 배치가 완료되면 설정된 수량만큼 아이템을 제거합니다.
- `damage`: 내구도가 있는 아이템 하나가 필요하며, 배치가 완료되면 `count`만큼 내구도 손상을 적용합니다. 이 손상으로 남은 내구도가 소진되면 아이템이 파손되며, 배치의 생산물은 정상적으로 나옵니다.
- `catalyst`: 조건에 맞는 아이템이 `count`개 필요하며 소모하지 않습니다.

정상적인 틱 속도에서는 20틱이 1초입니다. 단계별 전력 소비, 증류 모드, 승온과 출력 막힘에 따라 실제 처리 시간이나 전력 소비량이 달라질 수 있습니다. 장비별 운전 규칙은 [게임플레이 가이드](GAMEPLAY.kr.md)를 참고합니다. 레시피를 바꿔도 장비의 온도 특성이나 클린룸 요구 조건은 바뀌지 않습니다.

## 입력 일치와 유효성 검사

비정형 공정에서는 `shaped`를 `false`로 설정하고 모든 입력 슬롯에 `-1`을 사용합니다. 재료 조건이 서로 겹치더라도 아이템 한 개를 여러 조건에 중복해서 계산하지 않습니다. 같은 스택에 충분한 수량이 있으면 여러 조건에 나누어 사용할 수 있으며, 하나의 조건에 필요한 수량을 여러 스택에서 모을 수도 있습니다.

정형 공정은 재료를 지정된 슬롯에 넣어야 하며, 지정하지 않은 입력 슬롯은 비워 두어야 합니다. 비정형 공정은 입력 영역의 모든 아이템이 나열된 재료 조건 중 하나 이상과 일치해야 합니다. 일치하는 스택에는 한 배치에 필요한 수량보다 많이 넣어도 됩니다.

중복되거나 범위를 벗어난 정형 슬롯은 레시피를 불러올 때 거부됩니다. `ticks`, `energy_per_tick`, 입력 수량과 출력 수량은 `1`부터 `2,147,483,647`까지의 정수여야 합니다. 정수가 아닌 소수 값은 거부됩니다. 주 생산물과 부산물은 공기(`minecraft:air`)를 제외한 등록 아이템을 지정해야 하며, 각 수량은 해당 아이템의 최대 스택 크기를 초과할 수 없습니다.

`consume`과 `catalyst`의 수량은 장비의 실제 입력 공간에 맞춰 정합니다. 정형 입력은 지정된 한 슬롯의 스택에 필요한 수량이 모두 들어가야 합니다.

주 생산물과 모든 부산물을 담을 출력 공간을 확보합니다. 산업용 장비와 제조기는 출력 슬롯이 9칸입니다. 재순환기는 조 트리클로로실란용 4칸, 염화수소용 4칸, 기타 생산물용 1칸으로 구분되므로 사용자 공정의 결과물도 각 구역에 들어갈 수 있어야 합니다.

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

`input` 객체에는 공기(`minecraft:air`)를 제외한 등록 아이템의 `item` ID가 필요합니다. 양의 정수인 `count`를 지정할 수 있으며 기본값은 `1`입니다. 저장된 회로가 있는 웨이퍼도 아이템 종류만으로 일치하며, 태그나 회로 상태로 구분하지 않습니다. 필요한 수량은 재처리기의 입력 슬롯 9칸에서 합산할 수 있습니다.

`outputs`에는 비어 있지 않은 아이템 스택이 하나 이상 필요합니다. 각 출력에는 `item` ID가 필요하며 `count`의 기본값은 `1`입니다. 각 수량은 해당 아이템의 최대 스택 크기를 초과할 수 없으며 모든 결과물이 출력 슬롯 9칸에 들어갈 수 있어야 합니다. `ticks`와 `energy_per_tick`은 필수 양의 정수이며 장비 공정과 같은 단위 및 숫자 범위를 사용합니다.
