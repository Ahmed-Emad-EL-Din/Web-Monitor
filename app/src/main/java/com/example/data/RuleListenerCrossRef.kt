package com.example.data

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "rule_listener_cross_ref",
    primaryKeys = ["ruleId", "listenerId"],
    indices = [Index("listenerId")]
)
data class RuleListenerCrossRef(
    val ruleId: Int,
    val listenerId: Int
)
