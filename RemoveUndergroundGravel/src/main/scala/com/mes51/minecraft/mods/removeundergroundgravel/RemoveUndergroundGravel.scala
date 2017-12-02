package com.mes51.minecraft.mods.removeundergroundgravel

import java.io.File
import java.lang.reflect.Type

import com.google.gson._
import com.google.gson.reflect.TypeToken
import net.minecraft.block._
import net.minecraft.block.state.IBlockState

import collection.JavaConversions._
import collection.JavaConverters._
import net.minecraft.init.Blocks
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.minecraft.world.chunk.Chunk
import net.minecraftforge.event.terraingen.{DecorateBiomeEvent, OreGenEvent, PopulateChunkEvent}
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.Mod.{EventBusSubscriber, EventHandler}
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import org.apache.commons.io.FileUtils

object RemoveUndergroundGravelProps {
  final val MOD_ID = "removeundergroundgravel"
  final val NAME = "RemoveUndergroundGravel"
  final val VERSION = "1.0.0"
  final val MINECRAFT_VERSION = "[1.12]"
}

case class RemoveSetting(groundLevel: Int, removeTarget: List[IBlockState], replacer: IBlockState) {
  def isRemoveTarget(blockState: IBlockState): Boolean = {
    val block = blockState.getBlock
    val meta = block.getMetaFromState(blockState)
    removeTarget.exists(s => s.getBlock == block && block.getMetaFromState(s) == meta)
  }
}

object RemoveSettingSerializer extends JsonSerializer[RemoveSetting] {
  override def serialize(src: RemoveSetting, typeOfSrc: Type, context: JsonSerializationContext): JsonElement = {
    val obj = new JsonObject()

    val removeTargetObj = new JsonArray()
    src.removeTarget.map(s => context.serialize(s)).foreach(o => removeTargetObj.add(o))

    obj.addProperty("groundLevel", src.groundLevel)
    obj.add("removeTarget", removeTargetObj)
    obj.add("replacer", context.serialize(src.replacer))

    obj
  }
}

object RemoveSettingDeserializer extends  JsonDeserializer[RemoveSetting] {
  override def deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): RemoveSetting = {
    val obj = json.getAsJsonObject

    RemoveSetting(
      obj.get("groundLevel").getAsInt,
      obj.get("removeTarget").getAsJsonArray.map(o => context.deserialize[IBlockState](o, classOf[IBlockState])).toList,
      context.deserialize(obj.get("replacer"), classOf[IBlockState])
    )
  }
}

object BlockStateSerializer extends JsonSerializer[IBlockState] {
  override def serialize(src: IBlockState, typeOfSrc: Type, context: JsonSerializationContext): JsonElement = {
    val obj = new JsonObject()
    obj.addProperty("blockName", Block.REGISTRY.getNameForObject(src.getBlock).toString)
    obj.addProperty("meta", src.getBlock.getMetaFromState(src))
    obj
  }
}

object  BlockStateDesrializer extends JsonDeserializer[IBlockState] {
  override def deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): IBlockState = {
    val obj = json.getAsJsonObject
    val block = Block.REGISTRY.getObject(new ResourceLocation(obj.get("blockName").getAsString))
    block.getStateFromMeta(obj.get("meta").getAsInt)
  }
}

@Mod(
  name = RemoveUndergroundGravelProps.NAME,
  modid = RemoveUndergroundGravelProps.MOD_ID,
  version = RemoveUndergroundGravelProps.VERSION,
  acceptedMinecraftVersions = RemoveUndergroundGravelProps.MINECRAFT_VERSION,
  modLanguage = "scala"
)
@EventBusSubscriber
object RemoveUndergroundGravel {
  private val JSON_TYPE_TOKEN = new TypeToken[java.util.HashMap[java.lang.Integer, RemoveSetting]]() { }
  private val CHUNK_POSITIONS = product(0.until(16), 0.until(16)).toList
  private lazy val DEFAULT_REMOVE_SETTINGS = {
    val removeTarget = (BlockDirt.DirtType.values().map(t => Blocks.DIRT.getStateFromMeta(t.getMetadata)).toList :::
      BlockStone.EnumType.values().drop(1).map(t => Blocks.STONE.getStateFromMeta(t.getMetadata)).toList :::
      BlockSand.EnumType.values().map(t => Blocks.SAND.getStateFromMeta(t.getMetadata)).toList :::
      BlockSandStone.EnumType.values().map(t => Blocks.SANDSTONE.getStateFromMeta(t.getMetadata)).toList :::
      BlockRedSandstone.EnumType.values().map(t => Blocks.RED_SANDSTONE.getStateFromMeta(t.getMetadata)).toList) :+
      Blocks.GRAVEL.getStateFromMeta(0) :+
      Blocks.GRASS.getStateFromMeta(0)
    Map((0, RemoveSetting(61, removeTarget, Blocks.STONE.getStateFromMeta(0))))
  }

  private var removeSettings: Map[Int, RemoveSetting] = _

  @EventHandler
  def pre(e: FMLPreInitializationEvent): Unit = {
    val file = new File(e.getModConfigurationDirectory, "RemoveUndergroundGravel.json")
    if (file.exists()) {
      try {
        val builder = new GsonBuilder()
        builder.registerTypeAdapter(classOf[RemoveSetting], RemoveSettingDeserializer)
        builder.registerTypeHierarchyAdapter(classOf[IBlockState], BlockStateDesrializer)
        val javaResult: java.util.HashMap[Int, RemoveSetting] = builder.create().fromJson(FileUtils.readFileToString(file, "UTF-8"), JSON_TYPE_TOKEN.getType)
        removeSettings = javaResult.toMap
      } catch {
        case error: Throwable =>
          error.printStackTrace()
          removeSettings = DEFAULT_REMOVE_SETTINGS
      }
    } else {
      removeSettings = DEFAULT_REMOVE_SETTINGS
      val builder = new GsonBuilder()
      builder.registerTypeAdapter(classOf[RemoveSetting], RemoveSettingSerializer)
      builder.registerTypeHierarchyAdapter(classOf[IBlockState], BlockStateSerializer)
      builder.setPrettyPrinting()
      FileUtils.writeStringToFile(file, builder.create().toJson(removeSettings.asJava), "UTF-8")
    }
  }

  @SubscribeEvent
  def populateChunk(e: PopulateChunkEvent.Post): Unit = {
    removeSettings.get(e.getWorld.provider.getDimension)
      .foreach(s => {
        val chunk = e.getWorld.getChunkFromChunkCoords(e.getChunkX, e.getChunkZ)
        replaceBlock(chunk, s)
      })
  }

  @SubscribeEvent
  def decoratePre(e: DecorateBiomeEvent.Pre): Unit = processPreEvent(e)

  @SubscribeEvent
  def decoratePost(e: DecorateBiomeEvent.Post): Unit = processPostEvent(e)

  @SubscribeEvent
  def oreGenPre(e: OreGenEvent.Pre): Unit = processPreEvent(e)

  @SubscribeEvent
  def oreGenPost(e: OreGenEvent.Post): Unit = processPostEvent(e)

  private def product[A, B](as: Traversable[A], bs: Traversable[B]): Traversable[(A, B)] =
    for (a <- as; b <- bs) yield (a, b)

  private def replaceBlock(chunk: Chunk, removeSetting: RemoveSetting): Unit = {
    val posX = chunk.x << 4
    val posZ = chunk.z << 4

    CHUNK_POSITIONS.foreach(p => {
      val topBlockHeight = chunk.getHeightValue(p._1, p._2)
      Math.min(removeSetting.groundLevel, topBlockHeight).to(0, -1)
        .filter(y => removeSetting.isRemoveTarget(chunk.getBlockState(posX + p._1, y, posZ + p._2)))
        .foreach(y => chunk.setBlockState(new BlockPos(posX + p._1, y, posZ + p._2), removeSetting.replacer))
    })
  }

  private def processPreEvent(e: { def getWorld(): World }): Unit = {
    val world = e.getWorld()
    world.captureBlockSnapshots = removeSettings.contains(world.provider.getDimension)
  }

  private def processPostEvent(e: { def getWorld(): World }): Unit = {
    val world = e.getWorld()
    if (!world.captureBlockSnapshots) {
      return
    }
    world.captureBlockSnapshots = false

    val removeSetting = removeSettings(world.provider.getDimension)
    world.capturedBlockSnapshots
      .filter(p => p.getPos.getY <= removeSetting.groundLevel && removeSetting.isRemoveTarget(world.getBlockState(p.getPos)))
      .foreach(p => world.setBlockState(p.getPos, removeSetting.replacer))

    world.capturedBlockSnapshots.clear()
  }
}
