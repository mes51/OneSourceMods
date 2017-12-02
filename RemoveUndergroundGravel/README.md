# RemoveUndergroundGravel

---

## is何

* 指定した高度以下の特定のブロックを置き換えるMod
* それぞれ設定で変更可能

## 設定

```
{
    "0": {
        "groundLevel": 61,
        "removeTarget": [
            {
                "blockName": "minecraft:gravel",
		"meta": 0
            },
            ...
        ],
        "replacer": {
            "blockName": "minecraft:stone",
	    "meta": 0
        }
    }
}
```

### 項目

* "0"
    * dimension id
    * 数値ではあるけど仕様上文字列として書くこと
* groundLevel
    * どの高度から置き換え開始するか
* removeTarget
    * 置き換え対象のブロック
* replacer
    * 何に置き換えるか
* blockName
    * ブロックの内部名
* meta
    * ブロックのメタ

## 注意事項

* すでに生成済みのチャンクには効きません
* 重いです

## License

MIT
