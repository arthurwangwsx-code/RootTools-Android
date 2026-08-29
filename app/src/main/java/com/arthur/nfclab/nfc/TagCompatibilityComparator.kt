package com.arthur.nfclab.nfc

data class TagFieldDifference(
    val label: String,
    val left: String,
    val right: String,
)

data class TagCompatibilityComparison(
    val left: TagSnapshot,
    val right: TagSnapshot,
    val sameTechnologySet: Boolean,
    val sameProduct: Boolean?,
    val differences: List<TagFieldDifference>,
) {
    val summary: String
        get() = when {
            !sameTechnologySet -> "两张卡的 RF / Android Tech 栈不同，优先按不同卡型或协议路线排查。"
            sameProduct == true && differences.isEmpty() -> "公开协议指纹一致；若实际开门表现不同，差异更可能位于卡内应用、认证数据或读卡器策略层。"
            sameProduct == true -> "卡片产品族一致，但公开版本/参数存在差异，建议继续对照读卡器兼容性与应用层行为。"
            sameProduct == false -> "两张卡属于不同 NXP 产品/代际，不能按同一种门禁协议能力处理。"
            else -> "Android Tech 栈相近，但公开信息不足以确认两张卡是否为同一产品族。"
        }
}

object TagCompatibilityComparator {
    private val comparedFields = listOf(
        "NFC-A ATQA",
        "NFC-A SAK",
        "ISO-DEP historical bytes",
        "NXP product",
        "NXP product family",
        "NXP implementation",
        "NXP hardware version",
        "NXP software version",
        "NXP storage",
        "NXP storage code",
        "NXP protocol code",
    )

    fun compare(left: TagSnapshot, right: TagSnapshot): TagCompatibilityComparison {
        val leftTech = left.technologies.map { it.substringAfterLast('.') }.toSet()
        val rightTech = right.technologies.map { it.substringAfterLast('.') }.toSet()
        val leftProduct = left.details["NXP product"] ?: left.details["NXP product family"]
        val rightProduct = right.details["NXP product"] ?: right.details["NXP product family"]

        val differences = comparedFields.mapNotNull { key ->
            val leftValue = left.details[key]
            val rightValue = right.details[key]
            if (leftValue == null && rightValue == null) return@mapNotNull null
            if (leftValue == rightValue) return@mapNotNull null
            TagFieldDifference(
                label = key,
                left = leftValue ?: "未识别",
                right = rightValue ?: "未识别",
            )
        }

        return TagCompatibilityComparison(
            left = left,
            right = right,
            sameTechnologySet = leftTech == rightTech,
            sameProduct = when {
                leftProduct == null || rightProduct == null -> null
                else -> leftProduct == rightProduct
            },
            differences = differences,
        )
    }

    fun latestDistinctPair(history: List<TagSnapshot>): Pair<TagSnapshot, TagSnapshot>? {
        val distinct = history
            .sortedByDescending { it.timestampMs }
            .distinctBy { it.idHex }
            .take(2)
        return if (distinct.size == 2) distinct[0] to distinct[1] else null
    }
}
