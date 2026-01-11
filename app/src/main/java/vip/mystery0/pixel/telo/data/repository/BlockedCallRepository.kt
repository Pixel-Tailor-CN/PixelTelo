package vip.mystery0.pixel.telo.data.repository

import kotlinx.coroutines.flow.Flow
import vip.mystery0.pixel.telo.data.dao.BlockedCallDao
import vip.mystery0.pixel.telo.data.entity.BlockedCall

class BlockedCallRepository(private val blockedCallDao: BlockedCallDao) {
    val allBlockedCalls: Flow<List<BlockedCall>> = blockedCallDao.getAll()

    suspend fun insert(phoneNumber: String, remark: String?) {
        val blockedCall = BlockedCall(
            phoneNumber = phoneNumber,
            blockTime = System.currentTimeMillis(),
            remark = remark
        )
        blockedCallDao.insert(blockedCall)
    }

    suspend fun delete(blockedCall: BlockedCall) {
        blockedCallDao.delete(blockedCall)
    }
}
