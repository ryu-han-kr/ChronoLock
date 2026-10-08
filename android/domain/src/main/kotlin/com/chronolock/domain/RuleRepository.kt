package com.chronolock.domain

interface RuleRepository {
    suspend fun saveRule(rule: AppRule)
    suspend fun deleteRule(packageName: String)
}
