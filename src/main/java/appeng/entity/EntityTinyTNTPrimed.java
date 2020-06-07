/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.entity;


import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.item.TNTEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Explosion;
import net.minecraft.world.Explosion.Mode;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;

import appeng.core.AEConfig;
import appeng.core.AppEng;
import appeng.api.AEApi;
import appeng.api.features.AEFeature;
import appeng.core.sync.packets.PacketMockExplosion;
import appeng.helpers.Reflected;
import appeng.util.Platform;


public final class EntityTinyTNTPrimed extends TNTEntity implements IEntityAdditionalSpawnData
{

	public EntityTinyTNTPrimed( EntityType<? extends EntityTinyTNTPrimed> type, World worldIn )
	{
		super( type, worldIn );
		this.preventEntitySpawning = true;
	}

	public EntityTinyTNTPrimed( final World w, final double x, final double y, final double z, final LivingEntity igniter )
	{
		super( w, x, y, z, igniter );
	}

	/**
	 * Called to update the entity's position/logic.
	 */
	@Override
	public void tick()
	{
		this.handleWaterMovement();

		this.prevPosX = this.getPosX();
		this.prevPosY = this.getPosY();
		this.prevPosZ = this.getPosZ();
		this.setMotion( this.getMotion().subtract( 0, 0.03999999910593033D, 0 ) );
		this.move( MoverType.SELF, this.getMotion() );
		this.setMotion( this.getMotion().mul( 0.9800000190734863D, 0.9800000190734863D, 0.9800000190734863D ) );

		if( this.onGround )
		{
			this.setMotion( this.getMotion().mul( 0.699999988079071D, 0.699999988079071D, -0.5D ) );
		}

		if( this.isInWater() && Platform.isServer() ) // put out the fuse.
		{
			AEApi.instance().definitions().blocks().tinyTNT().maybeStack( 1 ).ifPresent( tntStack ->
			{
				final ItemEntity item = new ItemEntity( this.world, this.getPosX(), this.getPosY(), this.getPosZ(), tntStack );

				item.setMotion( this.getMotion() );
				item.prevPosX = this.prevPosX;
				item.prevPosY = this.prevPosY;
				item.prevPosZ = this.prevPosZ;

				this.world.addEntity( item );
				this.remove();
			} );
		}

		if( this.getFuse() <= 0 )
		{
			this.remove();

			if( !this.world.isRemote )
			{
				this.explode();
			}
		}
		else
		{
			this.world.addParticle( ParticleTypes.SMOKE, this.getPosX(), this.getPosY(), this.getPosZ(), 0.0D, 0.0D, 0.0D );
		}
		this.setFuse( this.getFuse() - 1 );
	}

	// override :P
	@Override
	protected void explode()
	{
		this.world.playSound( null, this.getPosX(), this.getPosY(), this.getPosZ(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 4.0F,
				( 1.0F + ( this.world.rand.nextFloat() - this.world.rand.nextFloat() ) * 0.2F ) * 32.9F );

		if( this.isInWater() )
		{
			return;
		}

		final Explosion ex = new Explosion( this.world, this, this.getPosX(), this.getPosY(), this.getPosZ(), 0.2f, false, Mode.BREAK );
		final AxisAlignedBB area = new AxisAlignedBB( this.getPosX() - 1.5, this.getPosY() - 1.5f, this
				.getPosZ() - 1.5, this.getPosX() + 1.5, this.getPosY() + 1.5, this.getPosZ() + 1.5 );
		final List<Entity> list = this.world.getEntitiesWithinAABBExcludingEntity( this, area );

		net.minecraftforge.event.ForgeEventFactory.onExplosionDetonate( this.world, ex, list, 0.2f * 2d );

		for( final Entity e : list )
		{
			e.attackEntityFrom( DamageSource.causeExplosionDamage( ex ), 6 );
		}

		if( AEConfig.instance().isFeatureEnabled( AEFeature.TINY_TNT_BLOCK_DAMAGE ) )
		{
			this.setPosition( this.getPosX(), this.getPosY() - 0.25, this.getPosZ() );

			for( int x = (int) ( this.getPosX() - 2 ); x <= this.getPosX() + 2; x++ )
			{
				for( int y = (int) ( this.getPosY() - 2 ); y <= this.getPosY() + 2; y++ )
				{
					for( int z = (int) ( this.getPosZ() - 2 ); z <= this.getPosZ() + 2; z++ )
					{
						final BlockPos point = new BlockPos( x, y, z );
						final BlockState state = this.world.getBlockState( point );
						final Block block = state.getBlock();

						if( block != null && !block.isAir( state, this.world, point ) )
						{
							float strength = (float) ( 2.3f - ( ( ( x + 0.5f ) - this.getPosX() ) * ( ( x + 0.5f ) - this
									.getPosX() ) + ( ( y + 0.5f ) - this.getPosY() ) * ( ( y + 0.5f ) - this.getPosY() ) + ( ( z + 0.5f ) - this
											.getPosZ() ) * ( ( z + 0.5f ) - this.getPosZ() ) ) );

							final float resistance = block.getExplosionResistance( state, this.world, point, this, ex );
							strength -= ( resistance + 0.3F ) * 0.11f;

							if( strength > 0.01 )
							{
								if( block.getMaterial( state ) != Material.AIR )
								{
									if( block.canDropFromExplosion( ex ) )
									{
										block.spawnDrops( state, this.world, point );
									}

									block.onBlockExploded( null, this.world, point, ex );
								}
							}
						}
					}
				}
			}
		}

		AppEng.proxy.sendToAllNearExcept( null, this.getPosX(), this.getPosY(), this.getPosZ(), 64, this.world,
				new PacketMockExplosion( this.getPosX(), this.getPosY(), this.getPosZ() ) );
	}

	@Override
	public void writeSpawnData( PacketBuffer buffer )
	{
		buffer.writeByte( this.getFuse() );
	}

	@Override
	public void readSpawnData( PacketBuffer additionalData )
	{
		this.setFuse( additionalData.readByte() );
	}
}
