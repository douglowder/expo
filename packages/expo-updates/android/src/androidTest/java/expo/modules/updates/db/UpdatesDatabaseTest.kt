package expo.modules.updates.db

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.platform.app.InstrumentationRegistry
import expo.modules.updates.db.dao.AssetDao
import expo.modules.updates.db.dao.UpdateDao
import expo.modules.updates.db.entity.AssetEntity
import expo.modules.updates.db.entity.UpdateAssetEntity
import expo.modules.updates.db.entity.UpdateEntity
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*

@RunWith(AndroidJUnit4ClassRunner::class)
class UpdatesDatabaseTest {
  private lateinit var db: UpdatesDatabase
  private lateinit var updateDao: UpdateDao
  private lateinit var assetDao: AssetDao

  @Before
  fun createDb() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    db = Room.inMemoryDatabaseBuilder(context, UpdatesDatabase::class.java).build()
    updateDao = db.updateDao()
    assetDao = db.assetDao()
  }

  @After
  fun closeDb() {
    db.close()
  }

  @Test
  fun testInsertUpdate() {
    val uuid = UUID.randomUUID()
    val date = Date()
    val runtimeVersion = "1.0"
    val projectId = "https://exp.host/@esamelson/test-project"
    val testUpdate = UpdateEntity(uuid, date, runtimeVersion, projectId)

    updateDao.insertUpdate(testUpdate)

    val byId = updateDao.loadUpdateWithId(uuid)
    Assert.assertNotNull(byId)
    Assert.assertEquals(uuid, byId!!.id)
    Assert.assertEquals(date, byId.commitTime)
    Assert.assertEquals(runtimeVersion, byId.runtimeVersion)
    Assert.assertEquals(projectId, byId.scopeKey)

    updateDao.deleteUpdates(listOf(testUpdate))
    Assert.assertEquals(0, updateDao.loadAllUpdates().size.toLong())
  }

  @Test(expected = SQLiteConstraintException::class)
  fun testForeignKeys() {
    val uuid = UUID.randomUUID()
    val date = Date()
    val runtimeVersion = "1.0"
    val projectId = "https://exp.host/@esamelson/test-project"
    val testUpdate = UpdateEntity(uuid, date, runtimeVersion, projectId)

    updateDao.insertUpdate(testUpdate)

    try {
      assetDao._insertUpdateAsset(UpdateAssetEntity(uuid, 47))
    } finally {
      updateDao.deleteUpdates(listOf(testUpdate))
    }
  }

  @Test
  fun testMarkUpdateReady() {
    val uuid = UUID.randomUUID()
    val date = Date()
    val runtimeVersion = "1.0"
    val projectId = "https://exp.host/@esamelson/test-project"
    val testUpdate = UpdateEntity(uuid, date, runtimeVersion, projectId)

    updateDao.insertUpdate(testUpdate)
    Assert.assertEquals(0, updateDao.loadLaunchableUpdatesForScope(projectId).size.toLong())

    updateDao.markUpdateFinished(testUpdate)
    Assert.assertEquals(1, updateDao.loadLaunchableUpdatesForScope(projectId).size.toLong())

    updateDao.deleteUpdates(Arrays.asList(testUpdate))
    Assert.assertEquals(0, updateDao.loadAllUpdates().size.toLong())
  }

  @Test
  fun testDeleteUnusedAssets() {
    val runtimeVersion = "1.0"
    val projectId = "https://exp.host/@esamelson/test-project"
    val update1 = UpdateEntity(UUID.randomUUID(), Date(), runtimeVersion, projectId)
    val asset1 = AssetEntity("asset1", "png")
    val commonAsset = AssetEntity("commonAsset", "png")

    updateDao.insertUpdate(update1)
    assetDao.insertAssets(listOf(asset1, commonAsset), update1)

    val update2 = UpdateEntity(UUID.randomUUID(), Date(), runtimeVersion, projectId)
    val asset2 = AssetEntity("asset2", "png")
    updateDao.insertUpdate(update2)
    assetDao.insertAssets(listOf(asset2), update2)
    assetDao.addExistingAssetToUpdate(update2, commonAsset, false)

    val update3 = UpdateEntity(UUID.randomUUID(), Date(), runtimeVersion, projectId)
    val asset3 = AssetEntity("asset3", "png")
    updateDao.insertUpdate(update3)
    assetDao.insertAssets(listOf(asset3), update3)
    assetDao.addExistingAssetToUpdate(update3, commonAsset, false)

    // update 1 will be deleted
    // update 2 will have keep = false
    // update 3 will have keep = true
    updateDao.deleteUpdates(listOf(update1))
    updateDao.markUpdateFinished(update3)

    // check that test has been properly set up
    val allUpdates = updateDao.loadAllUpdates()
    Assert.assertEquals(2, allUpdates.size.toLong())
    for (update in allUpdates) {
      if (update.id == update2.id) {
        Assert.assertFalse(update.keep)
      } else {
        Assert.assertTrue(update.keep)
      }
    }
    Assert.assertNotNull(assetDao.loadAssetWithKey("asset1"))
    Assert.assertNotNull(assetDao.loadAssetWithKey("asset2"))
    Assert.assertNotNull(assetDao.loadAssetWithKey("asset3"))
    Assert.assertNotNull(assetDao.loadAssetWithKey("commonAsset"))

    assetDao.deleteUnusedAssets()
    Assert.assertNull(assetDao.loadAssetWithKey("asset1"))
    Assert.assertNull(assetDao.loadAssetWithKey("asset2"))
    Assert.assertNotNull(assetDao.loadAssetWithKey("asset3"))
    Assert.assertNotNull(assetDao.loadAssetWithKey("commonAsset"))
  }
}
