package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import com.badlogic.gdx.math.Rectangle;
import com.etheller.warsmash.parsers.jass.scope.CommonTriggerExecutionScope;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.PrimaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.SequenceUtils;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.PathingFlags;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.RemovablePathingMapInstance;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitStateListener.CUnitStateNotifier;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.GetAbilityByRawcodeVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.build.CAbilityBuildInProgress;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.cargohold.CAbilityCargoHold;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.CLevelingAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.hero.CAbilityHero;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.inventory.CAbilityInventory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.mine.CAbilityBlightedGoldMine;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.mine.CAbilityGoldMine;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorAttackListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorAttackMove;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorFollow;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorHoldPosition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorMove;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorPatrol;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorStop;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.build.AbilityDisableWhileUpgradingVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CRegenType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CTargetType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CWeaponType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrder;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrderNoTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrderTargetPoint;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrderTargetWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.region.CRegion;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.region.CRegionEnumFunction;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.region.CRegionManager;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.state.CUnitState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.JassGameEventsWar3;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.CUnitTypeJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.CWidgetEvent;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityTargetCheckReceiver;

public class CUnit extends CWidget {
	private static RegionCheckerImpl regionCheckerImpl = new RegionCheckerImpl();

	private War3ID typeId;
	private float facing; // degrees
	private float mana;
	private int maximumLife;
	private float lifeRegen;
	private float lifeRegenStrengthBonus;
	private float lifeRegenBonus;
	private float manaRegen;
	private float manaRegenIntelligenceBonus;
	private float manaRegenBonus;
	private int maximumMana;
	private int speed;
	private int agilityDefensePermanentBonus;
	private float agilityDefenseTemporaryBonus;
	private int permanentDefenseBonus;
	private float temporaryDefenseBonus;
	private float totalTemporaryDefenseBonus;

	private int currentDefenseDisplay;
	private float currentDefense;
	private float currentLifeRegenPerTick;
	private float currentManaRegenPerTick;

	private int cooldownEndTime = 0;
	private float flyHeight;
	private int playerIndex;

	private final List<CAbility> abilities = new ArrayList<>();

	private CBehavior currentBehavior;
	private final Queue<COrder> orderQueue = new LinkedList<>();
	private CUnitType unitType;

	private Rectangle collisionRectangle;
	private RemovablePathingMapInstance pathingInstance;

	private final EnumSet<CUnitClassification> classifications = EnumSet.noneOf(CUnitClassification.class);

	private int deathTurnTick;
	private boolean corpse;
	private boolean boneCorpse;

	private transient CUnitAnimationListener unitAnimationListener;

	// if you use triggers for this then the transient tag here becomes really
	// questionable -- it already was -- but I meant for those to inform us
	// which fields shouldn't be persisted if we do game state save later
	private transient CUnitStateNotifier stateNotifier = new CUnitStateNotifier();
	private transient List<StateListenerUpdate> stateListenersUpdates = new ArrayList<>();
	private float acquisitionRange;
	private transient static AutoAttackTargetFinderEnum autoAttackTargetFinderEnum = new AutoAttackTargetFinderEnum();

	private transient CBehaviorMove moveBehavior;
	private transient CBehaviorAttack attackBehavior;
	private transient CBehaviorAttackMove attackMoveBehavior;
	private transient CBehaviorFollow followBehavior;
	private transient CBehaviorPatrol patrolBehavior;
	private transient CBehaviorStop stopBehavior;
	private transient CBehaviorHoldPosition holdPositionBehavior;
	private boolean constructing = false;
	private boolean constructingPaused = false;
	private War3ID upgradeIdType = null;
	private float constructionProgress;
	private boolean hidden = false;
	private boolean paused = false;
	private boolean acceptingOrders = true;
	private boolean invulnerable = false;
	private CBehavior defaultBehavior;
	private COrder lastStartedOrder = null;
	private CUnit workerInside;
	private final War3ID[] buildQueue = new War3ID[WarsmashConstants.BUILD_QUEUE_SIZE];
	private final QueueItemType[] buildQueueTypes = new QueueItemType[WarsmashConstants.BUILD_QUEUE_SIZE];
	private boolean queuedUnitFoodPaid = false;
	private AbilityTarget rallyPoint;

	private int foodMade;
	private int foodUsed;

	private int triggerEditorCustomValue;

	private List<CUnitAttack> unitSpecificAttacks;
	private transient Set<CRegion> containingRegions = new LinkedHashSet<>();
	private transient Set<CRegion> priorContainingRegions = new LinkedHashSet<>();

	public CUnit(final int handleId, final int playerIndex, final float x, final float y, final float life,
			final War3ID typeId, final float facing, final float mana, final int maximumLife, final float lifeRegen,
			final int maximumMana, final int speed, final CUnitType unitType,
			final RemovablePathingMapInstance pathingInstance) {
		super(handleId, x, y, life);
		this.playerIndex = playerIndex;
		this.typeId = typeId;
		this.facing = facing;
		this.mana = mana;
		this.maximumLife = maximumLife;
		this.lifeRegen = lifeRegen;
		this.manaRegen = unitType.getManaRegen();
		this.maximumMana = maximumMana;
		this.speed = speed;
		this.pathingInstance = pathingInstance;
		this.flyHeight = unitType.getDefaultFlyingHeight();
		this.unitType = unitType;
		this.classifications.addAll(unitType.getClassifications());
		this.acquisitionRange = unitType.getDefaultAcquisitionRange();
		this.stopBehavior = new CBehaviorStop(this);
		this.defaultBehavior = this.stopBehavior;
		this.currentBehavior = this.defaultBehavior;
		computeDerivedFields();
	}

	public float getLifeRegenBonus() {
		return this.lifeRegenBonus;
	}

	public void setLifeRegenStrengthBonus(final float lifeRegenStrengthBonus) {
		this.lifeRegenStrengthBonus = lifeRegenStrengthBonus;
		computeDerivedFields();
	}

	private void computeDerivedFields() {
		this.currentDefenseDisplay = this.unitType.getDefense() + this.agilityDefensePermanentBonus
				+ this.permanentDefenseBonus;
		this.totalTemporaryDefenseBonus = this.temporaryDefenseBonus + this.agilityDefenseTemporaryBonus;
		this.currentDefense = this.currentDefenseDisplay + this.totalTemporaryDefenseBonus;
		this.currentLifeRegenPerTick = (this.lifeRegen + this.lifeRegenBonus + this.lifeRegenStrengthBonus)
				* WarsmashConstants.SIMULATION_STEP_TIME;
		this.currentManaRegenPerTick = (this.manaRegen + this.manaRegenBonus + this.manaRegenIntelligenceBonus)
				* WarsmashConstants.SIMULATION_STEP_TIME;
	}

	public void setManaRegenIntelligenceBonus(final float manaRegenIntelligenceBonus) {
		this.manaRegenIntelligenceBonus = manaRegenIntelligenceBonus;
		computeDerivedFields();
	}

	public void setLifeRegenBonus(final float lifeRegenBonus) {
		this.lifeRegenBonus = lifeRegenBonus;
		computeDerivedFields();
	}

	public void setManaRegenBonus(final float manaRegenBonus) {
		this.manaRegenBonus = manaRegenBonus;
		computeDerivedFields();
	}

	public void setAgilityDefensePermanentBonus(final int agilityDefensePermanentBonus) {
		this.agilityDefensePermanentBonus = agilityDefensePermanentBonus;
		computeDerivedFields();
	}

	public void setAgilityDefenseTemporaryBonus(final float agilityDefenseTemporaryBonus) {
		this.agilityDefenseTemporaryBonus = agilityDefenseTemporaryBonus;
		computeDerivedFields();
	}

	public void setPermanentDefenseBonus(final int permanentDefenseBonus) {
		this.permanentDefenseBonus = permanentDefenseBonus;
		computeDerivedFields();
	}

	public void setTemporaryDefenseBonus(final float temporaryDefenseBonus) {
		this.temporaryDefenseBonus = temporaryDefenseBonus;
		computeDerivedFields();
	}

	public float getTemporaryDefenseBonus() {
		return this.temporaryDefenseBonus;
	}

	public float getTotalTemporaryDefenseBonus() {
		return this.totalTemporaryDefenseBonus;
	}

	public int getCurrentDefenseDisplay() {
		return this.currentDefenseDisplay;
	}

	public void setUnitAnimationListener(final CUnitAnimationListener unitAnimationListener) {
		this.unitAnimationListener = unitAnimationListener;
		this.unitAnimationListener.playAnimation(true, PrimaryTag.STAND, SequenceUtils.EMPTY, 1.0f, true);
	}

	public CUnitAnimationListener getUnitAnimationListener() {
		return this.unitAnimationListener;
	}

	public void add(final CSimulation simulation, final CAbility ability) {
		this.abilities.add(ability);
		simulation.onAbilityAddedToUnit(this, ability);
		ability.onAdd(simulation, this);
	}

	public void remove(final CSimulation simulation, final CAbility ability) {
		this.abilities.remove(ability);
		simulation.onAbilityRemovedFromUnit(this, ability);
		ability.onRemove(simulation, this);
	}

	public War3ID getTypeId() {
		return this.typeId;
	}

	/**
	 * @return facing in DEGREES
	 */
	public float getFacing() {
		return this.facing;
	}

	public float getMana() {
		return this.mana;
	}

	public int getMaximumMana() {
		return this.maximumMana;
	}

	public int getMaximumLife() {
		return this.maximumLife;
	}

	public void setTypeId(final CSimulation game, final War3ID typeId) {
		game.getWorldCollision().removeUnit(this);
		this.typeId = typeId;
		final float lifeRatio = this.maximumLife == 0 ? 1 : (this.life / this.maximumLife);
		final float manaRatio = this.maximumMana == 0 ? Float.NaN : (this.mana / this.maximumMana);
		this.unitType = game.getUnitData().getUnitType(typeId);
		if (Float.isNaN(manaRatio)) {
			this.mana = this.unitType.getManaInitial();
		}
		else {
			this.maximumMana = this.unitType.getManaMaximum();
			this.mana = manaRatio * this.maximumMana;
		}
		this.maximumLife = this.unitType.getMaxLife();
		this.life = lifeRatio * this.maximumLife;
		this.lifeRegen = this.unitType.getLifeRegen();
		this.manaRegen = this.unitType.getManaRegen();
		this.flyHeight = this.unitType.getDefaultFlyingHeight();
		this.speed = this.unitType.getSpeed();
		this.classifications.clear();
		this.classifications.addAll(this.unitType.getClassifications());
		this.acquisitionRange = this.unitType.getDefaultAcquisitionRange();
		for (final CAbility ability : this.abilities) {
			ability.onRemove(game, this);
			game.onAbilityRemovedFromUnit(this, ability);
		}
		this.abilities.clear();
		game.getUnitData().addDefaultAbilitiesToUnit(game, game.getHandleIdAllocator(), this.unitType, false, -1,
				this.speed, this);
		computeDerivedFields();
		game.getWorldCollision().addUnit(this);
		game.unitUpdatedType(this, typeId);
	}

	public void setFacing(final float facing) {
		// java modulo output can be negative, but not if we
		// force positive and modulo again
		this.facing = ((facing % 360) + 360) % 360;
	}

	public void setMana(final float mana) {
		this.mana = mana;
	}

	public void setMaximumLife(final int maximumLife) {
		this.maximumLife = maximumLife;
	}

	public void setMaximumMana(final int maximumMana) {
		this.maximumMana = maximumMana;
	}

	public void setSpeed(final int speed) {
		this.speed = speed;
	}

	public int getSpeed() {
		return this.speed;
	}

	/**
	 * Updates one tick of simulation logic and return true if it's time to remove
	 * this unit from the game.
	 */
	public boolean update(final CSimulation game) {
		for (final StateListenerUpdate update : this.stateListenersUpdates) {
			switch (update.getUpdateType()) {
			case ADD:
				this.stateNotifier.subscribe(update.listener);
				break;
			case REMOVE:
				this.stateNotifier.unsubscribe(update.listener);
				break;
			}
		}
		this.stateListenersUpdates.clear();
		if (isDead()) {
			if (this.collisionRectangle != null) {
				// Moved this here because doing it on "kill" was able to happen in some cases
				// while also iterating over the units that are in the collision system, and
				// then it hit the "writing while iterating" problem.
				game.getWorldCollision().removeUnit(this);
			}
			final int gameTurnTick = game.getGameTurnTick();
			if (!this.corpse) {
				if (gameTurnTick > (this.deathTurnTick
						+ (int) (this.unitType.getDeathTime() / WarsmashConstants.SIMULATION_STEP_TIME))) {
					this.corpse = true;
					if (!this.unitType.isRaise()) {
						this.boneCorpse = true;
						// start final phase immediately for "cant raise" case
					}
					if (!this.unitType.isHero()) {
						if (!this.unitType.isDecay()) {
							// if we dont raise AND dont decay, then now that death anim is over
							// we just delete the unit
							return true;
						}
					}
					else {
						game.heroDeathEvent(this);
					}
					this.deathTurnTick = gameTurnTick;
				}
			}
			else if (!this.boneCorpse) {
				if (game.getGameTurnTick() > (this.deathTurnTick + (int) (game.getGameplayConstants().getDecayTime()
						/ WarsmashConstants.SIMULATION_STEP_TIME))) {
					this.boneCorpse = true;
					this.deathTurnTick = gameTurnTick;
				}
			}
			else if (game.getGameTurnTick() > (this.deathTurnTick
					+ (int) (getEndingDecayTime(game) / WarsmashConstants.SIMULATION_STEP_TIME))) {
				if (this.unitType.isHero()) {
					if (!getHeroData().isAwaitingRevive()) {
						setHidden(true);
						getHeroData().setAwaitingRevive(true);
						game.heroDissipateEvent(this);
					}
					return false;
				}
				return true;
			}
		}
		else if (!this.paused) {
			if ((this.rallyPoint != this) && (this.rallyPoint instanceof CUnit) && ((CUnit) this.rallyPoint).isDead()) {
				setRallyPoint(this);
			}
			if (this.constructing) {
				if (!this.constructingPaused) {
					this.constructionProgress += WarsmashConstants.SIMULATION_STEP_TIME;
				}
				final int buildTime;
				final boolean upgrading = isUpgrading();
				if (!upgrading) {
					buildTime = this.unitType.getBuildTime();
					if (!this.constructingPaused) {
						final float healthGain = (WarsmashConstants.SIMULATION_STEP_TIME / buildTime)
								* (this.maximumLife * (1.0f - WarsmashConstants.BUILDING_CONSTRUCT_START_LIFE));
						setLife(game, Math.min(this.life + healthGain, this.maximumLife));
					}
				}
				else {
					buildTime = game.getUnitData().getUnitType(this.upgradeIdType).getBuildTime();
				}
				if (this.constructionProgress >= buildTime) {
					this.constructing = false;
					this.constructingPaused = false;
					this.constructionProgress = 0;
					popoutWorker(game);
					final Iterator<CAbility> abilityIterator = this.abilities.iterator();
					while (abilityIterator.hasNext()) {
						final CAbility ability = abilityIterator.next();
						if (ability instanceof CAbilityBuildInProgress) {
							abilityIterator.remove();
						}
						else {
							ability.setDisabled(false);
							ability.setIconShowing(true);
						}
					}
					final CPlayer player = game.getPlayer(this.playerIndex);
					if (upgrading) {
						if (this.unitType.getFoodMade() != 0) {
							player.setFoodCap(player.getFoodCap() - this.unitType.getFoodMade());
						}
						setTypeId(game, this.upgradeIdType);
						this.upgradeIdType = null;
					}
					if (this.unitType.getFoodMade() != 0) {
						player.setFoodCap(player.getFoodCap() + this.unitType.getFoodMade());
					}
					player.removeTechtreeInProgress(this.unitType.getTypeId());
					player.addTechtreeUnlocked(this.unitType.getTypeId());
					game.unitConstructFinishEvent(this);
					if (upgrading) {
						// TODO shouldnt need to play stand here, probably
						getUnitAnimationListener().playAnimation(false, PrimaryTag.STAND, SequenceUtils.EMPTY, 1.0f,
								true);
					}
					this.stateNotifier.ordersChanged();
				}
			}
			else {
				final War3ID queuedRawcode = this.buildQueue[0];
				if (queuedRawcode != null) {
					// queue step forward
					if (this.queuedUnitFoodPaid) {
						this.constructionProgress += WarsmashConstants.SIMULATION_STEP_TIME;
					}
					else {
						if (this.buildQueueTypes[0] == QueueItemType.UNIT) {
							final CPlayer player = game.getPlayer(this.playerIndex);
							final CUnitType trainedUnitType = game.getUnitData().getUnitType(queuedRawcode);
							if (trainedUnitType.getFoodUsed() != 0) {
								final int newFoodUsed = player.getFoodUsed() + trainedUnitType.getFoodUsed();
								if (newFoodUsed <= player.getFoodCap()) {
									player.setFoodUsed(newFoodUsed);
									player.addTechtreeInProgress(queuedRawcode);
									this.queuedUnitFoodPaid = true;
								}
							}
							else {
								player.addTechtreeInProgress(queuedRawcode);
								this.queuedUnitFoodPaid = true;
							}
						}
						else if (this.buildQueueTypes[0] == QueueItemType.HERO_REVIVE) {
							final CPlayer player = game.getPlayer(this.playerIndex);
							final CUnitType trainedUnitType = game.getUnit(queuedRawcode.getValue()).getUnitType();
							final int newFoodUsed = player.getFoodUsed() + trainedUnitType.getFoodUsed();
							if (newFoodUsed <= player.getFoodCap()) {
								player.setFoodUsed(newFoodUsed);
								this.queuedUnitFoodPaid = true;
							}
						}
						else {
							this.queuedUnitFoodPaid = true;
							System.err.println(
									"Unpaid food for non unit queue item ???? Attempting to correct this by setting paid=true");
						}
					}
					if (this.buildQueueTypes[0] == QueueItemType.UNIT) {
						final CUnitType trainedUnitType = game.getUnitData().getUnitType(queuedRawcode);
						if (this.constructionProgress >= trainedUnitType.getBuildTime()) {
							this.constructionProgress = 0;
							final CUnit trainedUnit = game.createUnit(queuedRawcode, this.playerIndex, getX(), getY(),
									game.getGameplayConstants().getBuildingAngle());
							// dont add food cost to player 2x
							trainedUnit.setFoodUsed(trainedUnitType.getFoodUsed());
							final CPlayer player = game.getPlayer(this.playerIndex);
							player.setUnitFoodMade(trainedUnit, trainedUnitType.getFoodMade());
							player.removeTechtreeInProgress(queuedRawcode);
							player.addTechtreeUnlocked(queuedRawcode);
							// nudge the trained unit out around us
							trainedUnit.nudgeAround(game, this);
							game.unitTrainedEvent(this, trainedUnit);
							if (this.rallyPoint != null) {
								final int rallyOrderId = OrderIds.smart;
								this.rallyPoint.visit(
										UseAbilityOnTargetByIdVisitor.INSTANCE.reset(game, trainedUnit, rallyOrderId));
							}
							for (int i = 0; i < (this.buildQueue.length - 1); i++) {
								setBuildQueueItem(game, i, this.buildQueue[i + 1], this.buildQueueTypes[i + 1]);
							}
							setBuildQueueItem(game, this.buildQueue.length - 1, null, null);
							this.stateNotifier.queueChanged();
						}
					}
					else if (this.buildQueueTypes[0] == QueueItemType.HERO_REVIVE) {
						final CUnit revivingHero = game.getUnit(queuedRawcode.getValue());
						final CUnitType trainedUnitType = revivingHero.getUnitType();
						final CGameplayConstants gameplayConstants = game.getGameplayConstants();
						if (this.constructionProgress >= gameplayConstants.getHeroReviveTime(
								trainedUnitType.getBuildTime(), revivingHero.getHeroData().getHeroLevel())) {
							this.constructionProgress = 0;
							revivingHero.getHeroData().setReviving(false);
							revivingHero.getHeroData().setAwaitingRevive(false);
							revivingHero.corpse = false;
							revivingHero.boneCorpse = false;
							revivingHero.deathTurnTick = 0;
							revivingHero.setX(getX());
							revivingHero.setY(getY());
							game.getWorldCollision().addUnit(revivingHero);
							revivingHero.setPoint(getX(), getY(), game.getWorldCollision(), game.getRegionManager());
							revivingHero.setHidden(false);
							revivingHero.setLife(game,
									revivingHero.getMaximumLife() * gameplayConstants.getHeroReviveLifeFactor());
							revivingHero.setMana((revivingHero.getMaximumMana()
									* gameplayConstants.getHeroReviveManaFactor())
									+ (gameplayConstants.getHeroReviveManaStart() * trainedUnitType.getManaInitial()));
							// dont add food cost to player 2x
							revivingHero.setFoodUsed(trainedUnitType.getFoodUsed());
							final CPlayer player = game.getPlayer(this.playerIndex);
							player.setUnitFoodMade(revivingHero, trainedUnitType.getFoodMade());
							player.addTechtreeUnlocked(queuedRawcode);
							// nudge the trained unit out around us
							revivingHero.nudgeAround(game, this);
							game.unitRepositioned(revivingHero); // dont blend animation
							game.heroReviveEvent(this, revivingHero);
							if (this.rallyPoint != null) {
								final int rallyOrderId = OrderIds.smart;
								this.rallyPoint.visit(
										UseAbilityOnTargetByIdVisitor.INSTANCE.reset(game, revivingHero, rallyOrderId));
							}
							for (int i = 0; i < (this.buildQueue.length - 1); i++) {
								setBuildQueueItem(game, i, this.buildQueue[i + 1], this.buildQueueTypes[i + 1]);
							}
							setBuildQueueItem(game, this.buildQueue.length - 1, null, null);
							this.stateNotifier.queueChanged();
						}
					}
					else if (this.buildQueueTypes[0] == QueueItemType.RESEARCH) {
						final CUnitType trainedUnitType = game.getUnitData().getUnitType(queuedRawcode);
						if (this.constructionProgress >= trainedUnitType.getBuildTime()) {
							this.constructionProgress = 0;
							for (int i = 0; i < (this.buildQueue.length - 1); i++) {
								setBuildQueueItem(game, i, this.buildQueue[i + 1], this.buildQueueTypes[i + 1]);
							}
							setBuildQueueItem(game, this.buildQueue.length - 1, null, null);
							this.stateNotifier.queueChanged();
						}
					}
				}
				if (this.life < this.maximumLife) {
					final CRegenType lifeRegenType = getUnitType().getLifeRegenType();
					boolean active = false;
					switch (lifeRegenType) {
					case ALWAYS:
						active = true;
						break;
					case DAY:
						active = game.isDay();
						break;
					case NIGHT:
						active = game.isNight();
						break;
					case BLIGHT:
						active = PathingFlags.isPathingFlag(game.getPathingGrid().getPathing(getX(), getY()),
								PathingFlags.BLIGHTED);
						break;
					default:
						active = false;
					}
					if (active) {
						float lifePlusRegen = this.life + this.currentLifeRegenPerTick;
						if (lifePlusRegen > this.maximumLife) {
							lifePlusRegen = this.maximumLife;
						}
						this.life = lifePlusRegen;
						this.stateNotifier.lifeChanged();
					}
				}
				if (this.mana < this.maximumMana) {
					float manaPlusRegen = this.mana + this.currentManaRegenPerTick;
					if (manaPlusRegen > this.maximumMana) {
						manaPlusRegen = this.maximumMana;
					}
					this.mana = manaPlusRegen;
					this.stateNotifier.manaChanged();
				}
				for (int i = this.abilities.size() - 1; i >= 0; i--) {
					// okay if it removes self from this during onTick() because of reverse
					// iteration order
					this.abilities.get(i).onTick(game, this);
				}
				if (this.currentBehavior != null) {
					final CBehavior lastBehavior = this.currentBehavior;
					final int lastBehaviorHighlightOrderId = lastBehavior.getHighlightOrderId();
					this.currentBehavior = this.currentBehavior.update(game);
					if (lastBehavior != this.currentBehavior) {
						lastBehavior.end(game, false);
						this.currentBehavior.begin(game);
					}
					if (this.currentBehavior.getHighlightOrderId() != lastBehaviorHighlightOrderId) {
						this.stateNotifier.ordersChanged();
					}
				}
				else {
					// check to auto acquire targets
					autoAcquireAttackTargets(game, false);
				}
			}
		}
		return false;

	}

	private void popoutWorker(final CSimulation game) {
		if (this.workerInside != null) {
			this.workerInside.setInvulnerable(false);
			this.workerInside.setHidden(false);
			this.workerInside.setPaused(false);
			this.workerInside.nudgeAround(game, this);
			this.workerInside = null;
		}
	}

	public boolean autoAcquireAttackTargets(final CSimulation game, final boolean disableMove) {
		if (!getAttacks().isEmpty() && !this.unitType.getClassifications().contains(CUnitClassification.PEON)) {
			if (this.collisionRectangle != null) {
				tempRect.set(this.collisionRectangle);
			}
			else {
				tempRect.set(getX(), getY(), 0, 0);
			}
			final float halfSize = this.acquisitionRange;
			tempRect.x -= halfSize;
			tempRect.y -= halfSize;
			tempRect.width += halfSize * 2;
			tempRect.height += halfSize * 2;
			game.getWorldCollision().enumUnitsInRect(tempRect,
					autoAttackTargetFinderEnum.reset(game, this, disableMove));
			return autoAttackTargetFinderEnum.foundAnyTarget;
		}
		return false;
	}

	public float getEndingDecayTime(final CSimulation game) {
		if (isBuilding()) {
			return game.getGameplayConstants().getStructureDecayTime();
		}
		if (this.unitType.isHero()) {
			return game.getGameplayConstants().getDissipateTime();
		}
		return game.getGameplayConstants().getBoneDecayTime();
	}

	public void order(final CSimulation game, final COrder order, final boolean queue) {
		if (isDead()) {
			return;
		}

		if (order != null) {
			final CAbility ability = game.getAbility(order.getAbilityHandleId());
			if (ability != null) {
				if (!getAbilities().contains(ability)) {
					// not allowed to use ability of other unit...
					return;
				}
				// Allow the ability to response to the order without actually placing itself in
				// the queue, nor modifying (interrupting) the queue.
				if (!ability.checkBeforeQueue(game, this, order.getOrderId(), order.getTarget(game))) {
					this.stateNotifier.ordersChanged();
					return;
				}
			}
		}

		if ((this.lastStartedOrder != null) && this.lastStartedOrder.equals(order)
				&& (this.lastStartedOrder.getOrderId() == OrderIds.smart)) {
			// I skip your spammed move orders, TODO this will probably break some repeat
			// attack order or something later
			return;
		}
		if ((queue || !this.acceptingOrders) && ((this.currentBehavior != this.stopBehavior)
				&& (this.currentBehavior != this.holdPositionBehavior))) {
			this.orderQueue.add(order);
			this.stateNotifier.waypointsChanged();
		}
		else {
			setDefaultBehavior(this.stopBehavior);
			if (this.currentBehavior != null) {
				this.currentBehavior.end(game, true);
			}
			this.currentBehavior = beginOrder(game, order);
			if (this.currentBehavior != null) {
				this.currentBehavior.begin(game);
			}
			for (final COrder queuedOrder : this.orderQueue) {
				final int abilityHandleId = queuedOrder.getAbilityHandleId();
				final CAbility ability = game.getAbility(abilityHandleId);
				ability.onCancelFromQueue(game, this, queuedOrder.getOrderId());
			}
			this.orderQueue.clear();
			this.stateNotifier.ordersChanged();
			this.stateNotifier.waypointsChanged();
		}
	}

	public boolean order(final CSimulation simulation, final int orderId, final AbilityTarget target) {
		if (orderId == OrderIds.stop) {
			order(simulation, new COrderNoTarget(0, orderId, false), false);
			return true;
		}
		for (final CAbility ability : this.abilities) {
			final BooleanAbilityActivationReceiver activationReceiver = BooleanAbilityActivationReceiver.INSTANCE;
			ability.checkCanUse(simulation, this, orderId, activationReceiver);
			if (activationReceiver.isOk()) {
				if (target == null) {
					final BooleanAbilityTargetCheckReceiver<Void> booleanTargetReceiver = BooleanAbilityTargetCheckReceiver
							.<Void>getInstance().reset();
					ability.checkCanTargetNoTarget(simulation, this, orderId, booleanTargetReceiver);
					if (booleanTargetReceiver.isTargetable()) {
						order(simulation, new COrderNoTarget(ability.getHandleId(), orderId, false), false);
						return true;
					}
				}
				final boolean targetable = target.visit(new AbilityTargetVisitor<Boolean>() {
					@Override
					public Boolean accept(final AbilityPointTarget target) {
						final BooleanAbilityTargetCheckReceiver<AbilityPointTarget> booleanTargetReceiver = BooleanAbilityTargetCheckReceiver
								.<AbilityPointTarget>getInstance().reset();
						ability.checkCanTarget(simulation, CUnit.this, orderId, target, booleanTargetReceiver);
						final boolean pointTargetable = booleanTargetReceiver.isTargetable();
						if (pointTargetable) {
							order(simulation, new COrderTargetPoint(ability.getHandleId(), orderId, target, false),
									false);
						}
						return pointTargetable;
					}

					public Boolean acceptWidget(final CWidget target) {
						final BooleanAbilityTargetCheckReceiver<CWidget> booleanTargetReceiver = BooleanAbilityTargetCheckReceiver
								.<CWidget>getInstance().reset();
						ability.checkCanTarget(simulation, CUnit.this, orderId, target, booleanTargetReceiver);
						final boolean widgetTargetable = booleanTargetReceiver.isTargetable();
						if (widgetTargetable) {
							order(simulation,
									new COrderTargetWidget(ability.getHandleId(), orderId, target.getHandleId(), false),
									false);
						}
						return widgetTargetable;
					}

					@Override
					public Boolean accept(final CUnit target) {
						return acceptWidget(target);
					}

					@Override
					public Boolean accept(final CDestructable target) {
						return acceptWidget(target);
					}

					@Override
					public Boolean accept(final CItem target) {
						return acceptWidget(target);
					}
				});
				if (targetable) {
					return true;
				}
			}
		}
		return false;
	}

	private CBehavior beginOrder(final CSimulation game, final COrder order) {
		this.lastStartedOrder = order;
		CBehavior nextBehavior;
		if (order != null) {
			nextBehavior = order.begin(game, this);
		}
		else {
			nextBehavior = this.defaultBehavior;
		}
		return nextBehavior;
	}

	public CBehavior getCurrentBehavior() {
		return this.currentBehavior;
	}

	public List<CAbility> getAbilities() {
		return this.abilities;
	}

	public <T> T getAbility(final CAbilityVisitor<T> visitor) {
		for (final CAbility ability : this.abilities) {
			final T visited = ability.visit(visitor);
			if (visited != null) {
				return visited;
			}
		}
		return null;
	}

	public <T extends CAbility> T getFirstAbilityOfType(final Class<T> cAbilityClass) {
		for (final CAbility ability : this.abilities) {
			if (cAbilityClass.isAssignableFrom(ability.getClass())) {
				return (T) ability;
			}
		}
		return null;
	}

	public void setCooldownEndTime(final int cooldownEndTime) {
		this.cooldownEndTime = cooldownEndTime;
	}

	public int getCooldownEndTime() {
		return this.cooldownEndTime;
	}

	@Override
	public float getFlyHeight() {
		return this.flyHeight;
	}

	public void setFlyHeight(final float flyHeight) {
		this.flyHeight = flyHeight;
	}

	public int getPlayerIndex() {
		return this.playerIndex;
	}

	public void setPlayerIndex(final int playerIndex) {
		this.playerIndex = playerIndex;
	}

	public CUnitType getUnitType() {
		return this.unitType;
	}

	public void setCollisionRectangle(final Rectangle collisionRectangle) {
		this.collisionRectangle = collisionRectangle;
	}

	public Rectangle getCollisionRectangle() {
		return this.collisionRectangle;
	}

	public void setX(final float newX, final CWorldCollision collision, final CRegionManager regionManager) {
		final float prevX = getX();
		if (!isBuilding()) {
			setX(newX);
			collision.translate(this, newX - prevX, 0);
		}
		checkRegionEvents(regionManager);
	}

	public void setY(final float newY, final CWorldCollision collision, final CRegionManager regionManager) {
		final float prevY = getY();
		if (!isBuilding()) {
			setY(newY);
			collision.translate(this, 0, newY - prevY);
		}
		checkRegionEvents(regionManager);
	}

	public void setPointAndCheckUnstuck(final float newX, final float newY, final CSimulation game) {
		final CWorldCollision collision = game.getWorldCollision();
		final PathingGrid pathingGrid = game.getPathingGrid();
		;
		float outputX = newX, outputY = newY;
		int checkX = 0;
		int checkY = 0;
		float collisionSize;
		if (this.unitType.getBuildingPathingPixelMap() != null) {
			tempRect.setSize(this.unitType.getBuildingPathingPixelMap().getWidth() * 32,
					this.unitType.getBuildingPathingPixelMap().getHeight() * 32);
			collisionSize = tempRect.getWidth() / 2;
		}
		else if (this.collisionRectangle != null) {
			tempRect.set(this.collisionRectangle);
			collisionSize = this.unitType.getCollisionSize();
		}
		else {
			tempRect.setSize(16, 16);
			collisionSize = this.unitType.getCollisionSize();
		}
		final boolean repos = false;
		for (int i = 0; i < 300; i++) {
			final float centerX = newX + (checkX * 64);
			final float centerY = newY + (checkY * 64);
			tempRect.setCenter(centerX, centerY);
			if (!collision.intersectsAnythingOtherThan(tempRect, this, this.unitType.getMovementType())
					&& pathingGrid.isPathable(centerX, centerY, this.unitType.getMovementType(), collisionSize)) {
				outputX = centerX;
				outputY = centerY;
				break;
			}
			final double angle = ((((int) Math.floor(Math.sqrt((4 * i) + 1))) % 4) * Math.PI) / 2;
			checkX -= (int) Math.cos(angle);
			checkY -= (int) Math.sin(angle);
		}
		setPoint(outputX, outputY, collision, game.getRegionManager());
		game.unitRepositioned(this);
	}

	public void setPoint(final float newX, final float newY, final CWorldCollision collision,
			final CRegionManager regionManager) {
		final float prevX = getX();
		final float prevY = getY();
		setX(newX);
		setY(newY);
		if (!isBuilding()) {
			collision.translate(this, newX - prevX, newY - prevY);
		}
		checkRegionEvents(regionManager);
	}

	private void checkRegionEvents(final CRegionManager regionManager) {
		final Set<CRegion> temp = this.containingRegions;
		this.containingRegions = this.priorContainingRegions;
		this.priorContainingRegions = temp;
		this.containingRegions.clear();
		regionManager.checkRegions(
				this.collisionRectangle == null ? tempRect.set(getX(), getY(), 0, 0) : this.collisionRectangle,
				regionCheckerImpl.reset(this, regionManager));
		for (final CRegion region : this.priorContainingRegions) {
			if (!this.containingRegions.contains(region)) {
				regionManager.onUnitLeaveRegion(this, region);
			}
		}
	}

	public EnumSet<CUnitClassification> getClassifications() {
		return this.classifications;
	}

	public float getDefense() {
		return this.currentDefense;
	}

	@Override
	public float getImpactZ() {
		return this.unitType.getImpactZ();
	}

	public double angleTo(final AbilityTarget target) {
		final double dx = target.getX() - getX();
		final double dy = target.getY() - getY();
		return StrictMath.atan2(dy, dx);
	}

	public double distance(final AbilityTarget target) {
		double dx = StrictMath.abs(target.getX() - getX());
		double dy = StrictMath.abs(target.getY() - getY());
		final float thisCollisionSize = this.unitType.getCollisionSize();
		float targetCollisionSize;
		if (target instanceof CUnit) {
			final CUnitType targetUnitType = ((CUnit) target).getUnitType();
			targetCollisionSize = targetUnitType.getCollisionSize();
		}
		else {
			targetCollisionSize = 0; // TODO destructable collision size here
		}
		if (dx < 0) {
			dx = 0;
		}
		if (dy < 0) {
			dy = 0;
		}

		double groundDistance = StrictMath.sqrt((dx * dx) + (dy * dy)) - thisCollisionSize - targetCollisionSize;
		if (groundDistance < 0) {
			groundDistance = 0;
		}
		return groundDistance;
	}

	public double distance(final float x, final float y) {
		double dx = Math.abs(x - getX());
		double dy = Math.abs(y - getY());
		final float thisCollisionSize = this.unitType.getCollisionSize();
		if (dx < 0) {
			dx = 0;
		}
		if (dy < 0) {
			dy = 0;
		}

		double groundDistance = StrictMath.sqrt((dx * dx) + (dy * dy)) - thisCollisionSize;
		if (groundDistance < 0) {
			groundDistance = 0;
		}
		return groundDistance;
	}

	@Override
	public void damage(final CSimulation simulation, final CUnit source, final CAttackType attackType,
			final String weaponType, final float damage) {
		final boolean wasDead = isDead();
		if (!this.invulnerable) {
			final float damageRatioFromArmorClass = simulation.getGameplayConstants().getDamageRatioAgainst(attackType,
					this.unitType.getDefenseType());
			final float damageRatioFromDefense;
			final float defense = this.currentDefense;
			if (defense >= 0) {
				damageRatioFromDefense = 1f - (((defense) * simulation.getGameplayConstants().getDefenseArmor())
						/ (1 + (simulation.getGameplayConstants().getDefenseArmor() * defense)));
			}
			else {
				damageRatioFromDefense = 2f - (float) StrictMath.pow(0.94, -defense);
			}
			final float trueDamage = damageRatioFromArmorClass * damageRatioFromDefense * damage;
			final boolean wasAboveMax = this.life > this.maximumLife;
			this.life -= trueDamage;
			if ((damage < 0) && !wasAboveMax && (this.life > this.maximumLife)) {
				// NOTE wasAboveMax is for that weird life drain power to drain above max... to
				// be honest that's a crazy mechanic anyway so I didn't test whether it works
				// yet
				this.life = this.maximumLife;
			}
			this.stateNotifier.lifeChanged();
		}
		simulation.unitDamageEvent(this, weaponType, this.unitType.getArmorType());
		if (!this.invulnerable && isDead()) {
			if (!wasDead) {
				kill(simulation, source);
			}
		}
		else {
			if ((this.currentBehavior == null) || (this.currentBehavior == this.defaultBehavior)) {
				boolean foundMatchingReturnFireAttack = false;
				if (!simulation.getPlayer(getPlayerIndex()).hasAlliance(source.getPlayerIndex(), CAllianceType.PASSIVE)
						&& !this.unitType.getClassifications().contains(CUnitClassification.PEON)) {
					for (final CUnitAttack attack : getAttacks()) {
						if (source.canBeTargetedBy(simulation, this, attack.getTargetsAllowed())) {
							this.currentBehavior = getAttackBehavior().reset(OrderIds.attack, attack, source, false,
									CBehaviorAttackListener.DO_NOTHING);
							this.currentBehavior.begin(simulation);
							foundMatchingReturnFireAttack = true;
							break;
						}
					}
				}
				if (!foundMatchingReturnFireAttack && this.unitType.isCanFlee() && !isMovementDisabled()
						&& (this.moveBehavior != null) && (this.playerIndex != source.getPlayerIndex())) {
					final double angleTo = source.angleTo(this);
					final int distanceToFlee = getSpeed();
					this.currentBehavior = this.moveBehavior.reset(OrderIds.move,
							new AbilityPointTarget((float) (getX() + (distanceToFlee * StrictMath.cos(angleTo))),
									(float) (getY() + (distanceToFlee * StrictMath.sin(angleTo)))));
					this.currentBehavior.begin(simulation);
				}
			}
		}
	}

	private void kill(final CSimulation simulation, final CUnit source) {
		if (this.currentBehavior != null) {
			this.currentBehavior.end(simulation, true);
		}
		this.currentBehavior = null;
		this.orderQueue.clear();
		if (this.constructing) {
			simulation.createBuildingDeathEffect(this);
		}
		else {
			this.deathTurnTick = simulation.getGameTurnTick();
		}
		if (this.pathingInstance != null) {
			this.pathingInstance.remove();
			this.pathingInstance = null;
		}
		popoutWorker(simulation);
		final CPlayer player = simulation.getPlayer(this.playerIndex);
		if (this.foodMade != 0) {
			player.setUnitFoodMade(this, 0);
		}
		if (this.foodUsed != 0) {
			player.setUnitFoodUsed(this, 0);
		}
		if (getHeroData() == null) {
			if (this.constructing) {
				player.removeTechtreeInProgress(this.unitType.getTypeId());
			}
			else {
				player.removeTechtreeUnlocked(this.unitType.getTypeId());
			}
		}
		// else its a hero and techtree "remains unlocked" which is currently meaning
		// the "limit of 1" remains limited

		// Award hero experience
		if (source != null) {
			final CPlayer sourcePlayer = simulation.getPlayer(source.getPlayerIndex());
			if (!sourcePlayer.hasAlliance(this.playerIndex, CAllianceType.PASSIVE)) {
				final CGameplayConstants gameplayConstants = simulation.getGameplayConstants();
				if (gameplayConstants.isBuildingKillsGiveExp() || !source.isBuilding()) {
					final CUnit killedUnit = this;
					final CAbilityHero killedUnitHeroData = getHeroData();
					final boolean killedUnitIsAHero = killedUnitHeroData != null;
					int availableAwardXp;
					if (killedUnitIsAHero) {
						availableAwardXp = gameplayConstants.getGrantHeroXP(killedUnitHeroData.getHeroLevel());
					}
					else {
						availableAwardXp = gameplayConstants.getGrantNormalXP(this.unitType.getLevel());
					}
					final List<CUnit> xpReceivingHeroes = new ArrayList<>();
					final int heroExpRange = gameplayConstants.getHeroExpRange();
					simulation.getWorldCollision().enumUnitsInRect(new Rectangle(getX() - heroExpRange,
							getY() - heroExpRange, heroExpRange * 2, heroExpRange * 2), new CUnitEnumFunction() {
								@Override
								public boolean call(final CUnit unit) {
									if ((unit.distance(killedUnit) <= heroExpRange)
											&& sourcePlayer.hasAlliance(unit.getPlayerIndex(), CAllianceType.SHARED_XP)
											&& (unit.getHeroData() != null)) {
										xpReceivingHeroes.add(unit);
									}
									return false;
								}
							});
					if (xpReceivingHeroes.isEmpty()) {
						if (gameplayConstants.isGlobalExperience()) {
							for (int i = 0; i < WarsmashConstants.MAX_PLAYERS; i++) {
								if (sourcePlayer.hasAlliance(i, CAllianceType.SHARED_XP)) {
									xpReceivingHeroes.addAll(simulation.getPlayerHeroes(i));
								}
							}
						}
					}
					for (final CUnit receivingHero : xpReceivingHeroes) {
						final CAbilityHero heroData = receivingHero.getHeroData();
						heroData.addXp(simulation, receivingHero,
								(int) (availableAwardXp * (1f / xpReceivingHeroes.size())
										* gameplayConstants.getHeroFactorXp(heroData.getHeroLevel())));
					}
				}
			}
		}
		for (int i = this.abilities.size() - 1; i >= 0; i--) {
			// okay if it removes self from this during onDeath() because of reverse
			// iteration order
			this.abilities.get(i).onDeath(simulation, this);
		}
		fireDeathEvents(simulation);
		final List<CWidgetEvent> eventList = getEventList(JassGameEventsWar3.EVENT_UNIT_DEATH);
		if (eventList != null) {
			for (final CWidgetEvent event : eventList) {
				event.fire(this, CommonTriggerExecutionScope.unitDeathScope(JassGameEventsWar3.EVENT_UNIT_DEATH,
						event.getTrigger(), this, source));
			}
		}
		simulation.getPlayer(this.playerIndex).fireUnitDeathEvents(this, source);
	}

	public boolean canReach(final AbilityTarget target, final float range) {
		final double distance = distance(target);
		if (target instanceof CUnit) {
			final CUnit targetUnit = (CUnit) target;
			final CUnitType targetUnitType = targetUnit.getUnitType();
			if (targetUnit.isBuilding() && (targetUnitType.getBuildingPathingPixelMap() != null)) {
				final BufferedImage buildingPathingPixelMap = targetUnitType.getBuildingPathingPixelMap();
				final float targetX = target.getX();
				final float targetY = target.getY();
				if (canReachToPathing(range, targetUnit.getFacing(), buildingPathingPixelMap, targetX, targetY)) {
					return true;
				}
			}
		}
		else if (target instanceof CDestructable) {
			final CDestructable targetDest = (CDestructable) target;
			final CDestructableType targetDestType = targetDest.getDestType();
			final BufferedImage pathingPixelMap = targetDest.isDead() ? targetDestType.getPathingDeathPixelMap()
					: targetDestType.getPathingPixelMap();
			final float targetX = target.getX();
			final float targetY = target.getY();
			if ((pathingPixelMap != null) && canReachToPathing(range, 270, pathingPixelMap, targetX, targetY)) {
				return true;
			}
		}
		return distance <= range;
	}

	public boolean canReach(final float x, final float y, final float range) {
		return distance(x, y) <= range; // TODO use dist squared for performance
	}

	public boolean canReachToPathing(final float range, final float rotationForPathing,
			final BufferedImage buildingPathingPixelMap, final float targetX, final float targetY) {
		if (buildingPathingPixelMap == null) {
			return canReach(targetX, targetY, range);
		}
		final int rotation = ((int) rotationForPathing + 450) % 360;
		final float relativeOffsetX = getX() - targetX;
		final float relativeOffsetY = getY() - targetY;
		final int gridWidth = ((rotation % 180) != 0) ? buildingPathingPixelMap.getHeight()
				: buildingPathingPixelMap.getWidth();
		final int gridHeight = ((rotation % 180) != 0) ? buildingPathingPixelMap.getWidth()
				: buildingPathingPixelMap.getHeight();
		final int relativeGridX = (int) Math.floor(relativeOffsetX / 32f) + (gridWidth / 2);
		final int relativeGridY = (int) Math.floor(relativeOffsetY / 32f) + (gridHeight / 2);
		final int rangeInCells = (int) Math.floor(range / 32f) + 1;
		final int rangeInCellsSquare = rangeInCells * rangeInCells;
		int minCheckX = relativeGridX - rangeInCells;
		int minCheckY = relativeGridY - rangeInCells;
		int maxCheckX = relativeGridX + rangeInCells;
		int maxCheckY = relativeGridY + rangeInCells;
		if ((minCheckX < gridWidth) && (maxCheckX >= 0)) {
			if ((minCheckY < gridHeight) && (maxCheckY >= 0)) {
				if (minCheckX < 0) {
					minCheckX = 0;
				}
				if (minCheckY < 0) {
					minCheckY = 0;
				}
				if (maxCheckX > (gridWidth - 1)) {
					maxCheckX = gridWidth - 1;
				}
				if (maxCheckY > (gridHeight - 1)) {
					maxCheckY = gridHeight - 1;
				}
				for (int checkX = minCheckX; checkX <= maxCheckX; checkX++) {
					for (int checkY = minCheckY; checkY <= maxCheckY; checkY++) {
						final int dx = relativeGridX - checkX;
						final int dy = relativeGridY - checkY;
						if (((dx * dx) + (dy * dy)) <= rangeInCellsSquare) {
							if (((getRGBFromPixelData(buildingPathingPixelMap, checkX, checkY, rotation)
									& 0xFF0000) >>> 16) > 127) {
								return true;
							}
						}
					}
				}
			}
		}
		return false;
	}

	private int getRGBFromPixelData(final BufferedImage buildingPathingPixelMap, final int checkX, final int checkY,
			final int rotation) {

		// Below: y is downwards (:()
		int x;
		int y;
		switch (rotation) {
		case 90:
			x = checkY;
			y = buildingPathingPixelMap.getWidth() - 1 - checkX;
			break;
		case 180:
			x = buildingPathingPixelMap.getWidth() - 1 - checkX;
			y = buildingPathingPixelMap.getHeight() - 1 - checkY;
			break;
		case 270:
			x = buildingPathingPixelMap.getHeight() - 1 - checkY;
			y = checkX;
			break;
		default:
		case 0:
			x = checkX;
			y = checkY;
		}
		return buildingPathingPixelMap.getRGB(x, buildingPathingPixelMap.getHeight() - 1 - y);
	}

	public void addStateListener(final CUnitStateListener listener) {
		this.stateListenersUpdates.add(new StateListenerUpdate(listener, StateListenerUpdateType.ADD));
	}

	public void removeStateListener(final CUnitStateListener listener) {
		this.stateListenersUpdates.add(new StateListenerUpdate(listener, StateListenerUpdateType.REMOVE));
	}

	public boolean isCorpse() {
		return this.corpse;
	}

	public boolean isBoneCorpse() {
		return this.boneCorpse;
	}

	@Override
	public boolean canBeTargetedBy(final CSimulation simulation, final CUnit source,
			final EnumSet<CTargetType> targetsAllowed) {
		if ((this == source) && targetsAllowed.contains(CTargetType.NOTSELF)
				&& !targetsAllowed.contains(CTargetType.SELF)) {
			return false;
		}
		if (targetsAllowed.containsAll(this.unitType.getTargetedAs()) || (!targetsAllowed.contains(CTargetType.GROUND)
				&& (!targetsAllowed.contains(CTargetType.STRUCTURE) && !targetsAllowed.contains(CTargetType.AIR)))) {
			final int sourcePlayerIndex = source.getPlayerIndex();
			final CPlayer sourcePlayer = simulation.getPlayer(sourcePlayerIndex);
			if (!targetsAllowed.contains(CTargetType.ENEMIES)
					|| !sourcePlayer.hasAlliance(this.playerIndex, CAllianceType.PASSIVE)) {
				if (isDead()) {
					if (this.unitType.isRaise() && this.unitType.isDecay() && isBoneCorpse()) {
						return targetsAllowed.contains(CTargetType.DEAD);
					}
				}
				else {
					return !targetsAllowed.contains(CTargetType.DEAD) || targetsAllowed.contains(CTargetType.ALIVE);
				}
			}
		}
		else {
			System.err.println("No targeting because " + targetsAllowed + " does not contain all of "
					+ this.unitType.getTargetedAs());
		}
		return false;
	}

	public boolean isMovementDisabled() {
		return this.moveBehavior == null;
		// TODO this used to directly return the state of whether our unit was a
		// building. Will it be a problem that I changed it?
		// I was trying to fix attack move on stationary units which was crashing
	}

	public float getAcquisitionRange() {
		return this.acquisitionRange;
	}

	public void setAcquisitionRange(final float acquisitionRange) {
		this.acquisitionRange = acquisitionRange;
	}

	public void heal(final CSimulation game, final int lifeToRegain) {
		setLife(game, Math.min(getLife() + lifeToRegain, getMaximumLife()));
	}

	public void restoreMana(final CSimulation game, final int manaToRegain) {
		setMana(Math.min(getMana() + manaToRegain, getMaximumMana()));
	}

	private static final class AutoAttackTargetFinderEnum implements CUnitEnumFunction {
		private CSimulation game;
		private CUnit source;
		private boolean disableMove;
		private boolean foundAnyTarget;

		private AutoAttackTargetFinderEnum reset(final CSimulation game, final CUnit source,
				final boolean disableMove) {
			this.game = game;
			this.source = source;
			this.disableMove = disableMove;
			this.foundAnyTarget = false;
			return this;
		}

		@Override
		public boolean call(final CUnit unit) {
			if (!this.game.getPlayer(this.source.getPlayerIndex()).hasAlliance(unit.getPlayerIndex(),
					CAllianceType.PASSIVE) && !unit.isDead() && !unit.isInvulnerable()) {
				for (final CUnitAttack attack : this.source.getAttacks()) {
					if (this.source.canReach(unit, this.source.acquisitionRange)
							&& unit.canBeTargetedBy(this.game, this.source, attack.getTargetsAllowed())
							&& (this.source.distance(unit) >= this.source.getUnitType().getMinimumAttackRange())) {
						if (this.source.currentBehavior != null) {
							this.source.currentBehavior.end(this.game, false);
						}
						this.source.currentBehavior = this.source.getAttackBehavior().reset(OrderIds.attack, attack,
								unit, this.disableMove, CBehaviorAttackListener.DO_NOTHING);
						this.source.currentBehavior.begin(this.game);
						this.foundAnyTarget = true;
						return true;
					}
				}
			}
			return false;
		}
	}

	public CBehaviorMove getMoveBehavior() {
		return this.moveBehavior;
	}

	public void setMoveBehavior(final CBehaviorMove moveBehavior) {
		this.moveBehavior = moveBehavior;
	}

	public CBehaviorAttack getAttackBehavior() {
		return this.attackBehavior;
	}

	public void setAttackBehavior(final CBehaviorAttack attackBehavior) {
		this.attackBehavior = attackBehavior;
	}

	public void setAttackMoveBehavior(final CBehaviorAttackMove attackMoveBehavior) {
		this.attackMoveBehavior = attackMoveBehavior;
	}

	public CBehaviorAttackMove getAttackMoveBehavior() {
		return this.attackMoveBehavior;
	}

	public CBehaviorStop getStopBehavior() {
		return this.stopBehavior;
	}

	public void setFollowBehavior(final CBehaviorFollow followBehavior) {
		this.followBehavior = followBehavior;
	}

	public void setPatrolBehavior(final CBehaviorPatrol patrolBehavior) {
		this.patrolBehavior = patrolBehavior;
	}

	public void setHoldPositionBehavior(final CBehaviorHoldPosition holdPositionBehavior) {
		this.holdPositionBehavior = holdPositionBehavior;
	}

	public CBehaviorFollow getFollowBehavior() {
		return this.followBehavior;
	}

	public CBehaviorPatrol getPatrolBehavior() {
		return this.patrolBehavior;
	}

	public CBehaviorHoldPosition getHoldPositionBehavior() {
		return this.holdPositionBehavior;
	}

	public CBehavior pollNextOrderBehavior(final CSimulation game) {
		if (this.defaultBehavior != this.stopBehavior) {
			// kind of a stupid hack, meant to align in feel with some behaviors that were
			// observed on War3
			return this.defaultBehavior;
		}
		final COrder order = this.orderQueue.poll();
		final CBehavior nextOrderBehavior = beginOrder(game, order);
		this.stateNotifier.waypointsChanged();
		return nextOrderBehavior;
	}

	public boolean isMoving() {
		return getCurrentBehavior() instanceof CBehaviorMove;
	}

	public void setConstructing(final boolean constructing) {
		this.constructing = constructing;
		if (constructing) {
			this.unitAnimationListener.playAnimation(true, PrimaryTag.BIRTH, SequenceUtils.EMPTY, 0.0f, true);
		}
	}

	public void setConstructingPaused(final boolean constructingPaused) {
		this.constructingPaused = constructingPaused;
	}

	public void setConstructionProgress(final float constructionProgress) {
		this.constructionProgress = constructionProgress;
	}

	public boolean isConstructing() {
		return this.constructing && (this.upgradeIdType == null);
	}

	public boolean isUpgrading() {
		return this.constructing && (this.upgradeIdType != null);
	}

	public War3ID getUpgradeIdType() {
		return this.upgradeIdType;
	}

	public boolean isConstructingOrUpgrading() {
		return this.constructing;
	}

	public float getConstructionProgress() {
		return this.constructionProgress;
	}

	public void setHidden(final boolean hidden) {
		this.hidden = hidden;
	}

	public void setPaused(final boolean paused) {
		this.paused = paused;
	}

	public void setAcceptingOrders(final boolean acceptingOrders) {
		this.acceptingOrders = acceptingOrders;
	}

	public boolean isHidden() {
		return this.hidden;
	}

	public void setInvulnerable(final boolean invulnerable) {
		this.invulnerable = invulnerable;
	}

	@Override
	public boolean isInvulnerable() {
		return this.invulnerable;
	}

	public void setWorkerInside(final CUnit unit) {
		this.workerInside = unit;
	}

	public CUnit getWorkerInside() {
		return this.workerInside;
	}

	private void nudgeAround(final CSimulation simulation, final CUnit structure) {
		setPointAndCheckUnstuck(structure.getX(), structure.getY(), simulation);
	}

	@Override
	public void setLife(final CSimulation simulation, final float life) {
		final boolean wasDead = isDead();
		super.setLife(simulation, life);
		if (isDead() && !wasDead) {
			kill(simulation, null);
		}
		this.stateNotifier.lifeChanged();
	}

	private boolean queue(final CSimulation game, final War3ID rawcode, final QueueItemType queueItemType) {
		for (int i = 0; i < this.buildQueue.length; i++) {
			if (this.buildQueue[i] == null) {
				setBuildQueueItem(game, i, rawcode, queueItemType);
				return true;
			}
		}
		return false;
	}

	public War3ID[] getBuildQueue() {
		return this.buildQueue;
	}

	public QueueItemType[] getBuildQueueTypes() {
		return this.buildQueueTypes;
	}

	public float getBuildQueueTimeRemaining(final CSimulation simulation) {
		if (this.buildQueueTypes[0] == null) {
			return 0;
		}
		switch (this.buildQueueTypes[0]) {
		case RESEARCH:
			return 999; // TODO
		case UNIT: {
			final CUnitType trainedUnitType = simulation.getUnitData().getUnitType(this.buildQueue[0]);
			return trainedUnitType.getBuildTime();
		}
		case HERO_REVIVE: {
			final CUnit hero = simulation.getUnit(this.buildQueue[0].getValue());
			final CUnitType trainedUnitType = hero.getUnitType();
			return simulation.getGameplayConstants().getHeroReviveTime(trainedUnitType.getBuildTime(),
					hero.getHeroData().getHeroLevel());
		}
		default:
			return 0;
		}
	}

	public void cancelBuildQueueItem(final CSimulation game, final int cancelIndex) {
		if ((cancelIndex >= 0) && (cancelIndex < this.buildQueueTypes.length)) {
			final QueueItemType cancelledType = this.buildQueueTypes[cancelIndex];
			if (cancelledType != null) {
				// TODO refund here!
				if (cancelIndex == 0) {
					this.constructionProgress = 0.0f;
					switch (cancelledType) {
					case RESEARCH:
						break;
					case UNIT: {
						final CPlayer player = game.getPlayer(this.playerIndex);
						final CUnitType unitType = game.getUnitData().getUnitType(this.buildQueue[cancelIndex]);
						player.setFoodUsed(player.getFoodUsed() - unitType.getFoodUsed());
						player.removeTechtreeInProgress(this.buildQueue[cancelIndex]);
						break;
					}
					case HERO_REVIVE: {
						final CPlayer player = game.getPlayer(this.playerIndex);
						final CUnitType unitType = game.getUnit(this.buildQueue[cancelIndex].getValue()).getUnitType();
						player.setFoodUsed(player.getFoodUsed() - unitType.getFoodUsed());
						break;
					}
					}
				}
				switch (cancelledType) {
				case RESEARCH:
					break;
				case UNIT: {
					final CPlayer player = game.getPlayer(this.playerIndex);
					final CUnitType unitType = game.getUnitData().getUnitType(this.buildQueue[cancelIndex]);
					player.refundFor(unitType);
					break;
				}
				case HERO_REVIVE: {
					final CPlayer player = game.getPlayer(this.playerIndex);
					final CUnit hero = game.getUnit(this.buildQueue[cancelIndex].getValue());
					final CUnitType unitType = hero.getUnitType();
					final CAbilityHero heroData = hero.getHeroData();
					heroData.setReviving(false);
					final CGameplayConstants gameplayConstants = game.getGameplayConstants();
					player.refund(
							gameplayConstants.getHeroReviveGoldCost(unitType.getGoldCost(), heroData.getHeroLevel()),
							gameplayConstants.getHeroReviveLumberCost(unitType.getLumberCost(),
									heroData.getHeroLevel()));
					break;
				}
				}
				for (int i = cancelIndex; i < (this.buildQueueTypes.length - 1); i++) {
					setBuildQueueItem(game, i, this.buildQueue[i + 1], this.buildQueueTypes[i + 1]);
				}
				setBuildQueueItem(game, this.buildQueue.length - 1, null, null);
				this.stateNotifier.queueChanged();
			}
		}
	}

	public void setBuildQueueItem(final CSimulation game, final int index, final War3ID rawcode,
			final QueueItemType queueItemType) {
		this.buildQueue[index] = rawcode;
		this.buildQueueTypes[index] = queueItemType;
		if (index == 0) {
			this.queuedUnitFoodPaid = true;
			if (rawcode != null) {
				if (queueItemType == QueueItemType.UNIT) {
					final CPlayer player = game.getPlayer(this.playerIndex);
					final CUnitType unitType = game.getUnitData().getUnitType(this.buildQueue[index]);
					if (unitType.getFoodUsed() != 0) {
						final int newFoodUsed = player.getFoodUsed() + unitType.getFoodUsed();
						if (newFoodUsed <= player.getFoodCap()) {
							player.setFoodUsed(newFoodUsed);
							player.addTechtreeInProgress(rawcode);
						}
						else {
							this.queuedUnitFoodPaid = false;
							game.getCommandErrorListener().showNoFoodError(this.playerIndex);
						}
					}
					else {
						player.addTechtreeInProgress(rawcode);
					}
				}
				else if (queueItemType == QueueItemType.HERO_REVIVE) {
					final CPlayer player = game.getPlayer(this.playerIndex);
					final CUnitType unitType = game.getUnit(this.buildQueue[index].getValue()).getUnitType();
					if (unitType.getFoodUsed() != 0) {
						final int newFoodUsed = player.getFoodUsed() + unitType.getFoodUsed();
						if (newFoodUsed <= player.getFoodCap()) {
							player.setFoodUsed(newFoodUsed);
						}
						else {
							this.queuedUnitFoodPaid = false;
							game.getCommandErrorListener().showNoFoodError(this.playerIndex);
						}
					}
				}
			}
		}
	}

	public void queueTrainingUnit(final CSimulation game, final War3ID rawcode) {
		if (queue(game, rawcode, QueueItemType.UNIT)) {
			final CPlayer player = game.getPlayer(this.playerIndex);
			final CUnitType unitType = game.getUnitData().getUnitType(rawcode);
			final boolean isHeroType = unitType.isHero();
			if (isHeroType && (player.getHeroTokens() > 0)) {
				player.setHeroTokens(player.getHeroTokens() - 1);
			}
			else {
				player.chargeFor(unitType);
			}
		}
	}

	public void queueRevivingHero(final CSimulation game, final CUnit hero) {
		if (queue(game, new War3ID(hero.getHandleId()), QueueItemType.HERO_REVIVE)) {
			hero.getHeroData().setReviving(true);
			final CPlayer player = game.getPlayer(this.playerIndex);
			final int heroReviveGoldCost = game.getGameplayConstants()
					.getHeroReviveGoldCost(hero.getUnitType().getGoldCost(), hero.getHeroData().getHeroLevel());
			final int heroReviveLumberCost = game.getGameplayConstants()
					.getHeroReviveLumberCost(hero.getUnitType().getGoldCost(), hero.getHeroData().getHeroLevel());
			player.charge(heroReviveGoldCost, heroReviveLumberCost);
		}
	}

	public void queueResearch(final CSimulation game, final War3ID rawcode) {
		queue(game, rawcode, QueueItemType.RESEARCH);
	}

	public static enum QueueItemType {
		UNIT, RESEARCH, HERO_REVIVE;
	}

	public void setRallyPoint(final AbilityTarget target) {
		this.rallyPoint = target;
		this.stateNotifier.rallyPointChanged();
	}

	public void internalPublishHeroStatsChanged() {
		this.stateNotifier.heroStatsChanged();
	}

	public AbilityTarget getRallyPoint() {
		return this.rallyPoint;
	}

	private static interface RallyProvider {
		float getX();

		float getY();
	}

	@Override
	public <T> T visit(final AbilityTargetVisitor<T> visitor) {
		return visitor.accept(this);
	}

	@Override
	public <T> T visit(final CWidgetVisitor<T> visitor) {
		return visitor.accept(this);
	}

	private static final class UseAbilityOnTargetByIdVisitor implements AbilityTargetVisitor<Void> {
		private static final UseAbilityOnTargetByIdVisitor INSTANCE = new UseAbilityOnTargetByIdVisitor();
		private CSimulation game;
		private CUnit trainedUnit;
		private int rallyOrderId;

		private UseAbilityOnTargetByIdVisitor reset(final CSimulation game, final CUnit trainedUnit,
				final int rallyOrderId) {
			this.game = game;
			this.trainedUnit = trainedUnit;
			this.rallyOrderId = rallyOrderId;
			return this;
		}

		@Override
		public Void accept(final AbilityPointTarget target) {
			CAbility abilityToUse = null;
			for (final CAbility ability : this.trainedUnit.getAbilities()) {
				ability.checkCanUse(this.game, this.trainedUnit, this.rallyOrderId,
						BooleanAbilityActivationReceiver.INSTANCE);
				if (BooleanAbilityActivationReceiver.INSTANCE.isOk()) {
					final BooleanAbilityTargetCheckReceiver<AbilityPointTarget> targetCheckReceiver = BooleanAbilityTargetCheckReceiver
							.<AbilityPointTarget>getInstance().reset();
					ability.checkCanTarget(this.game, this.trainedUnit, this.rallyOrderId, target, targetCheckReceiver);
					if (targetCheckReceiver.isTargetable()) {
						abilityToUse = ability;
					}
				}
			}
			if (abilityToUse != null) {
				this.trainedUnit.order(this.game,
						new COrderTargetPoint(abilityToUse.getHandleId(), this.rallyOrderId, target, false), false);
			}
			return null;
		}

		@Override
		public Void accept(final CUnit targetUnit) {
			return acceptWidget(this.game, this.trainedUnit, this.rallyOrderId, targetUnit);
		}

		private Void acceptWidget(final CSimulation game, final CUnit trainedUnit, final int rallyOrderId,
				final CWidget target) {
			CAbility abilityToUse = null;
			for (final CAbility ability : trainedUnit.getAbilities()) {
				ability.checkCanUse(game, trainedUnit, rallyOrderId, BooleanAbilityActivationReceiver.INSTANCE);
				if (BooleanAbilityActivationReceiver.INSTANCE.isOk()) {
					final BooleanAbilityTargetCheckReceiver<CWidget> targetCheckReceiver = BooleanAbilityTargetCheckReceiver
							.<CWidget>getInstance().reset();
					ability.checkCanTarget(game, trainedUnit, rallyOrderId, target, targetCheckReceiver);
					if (targetCheckReceiver.isTargetable()) {
						abilityToUse = ability;
					}
				}
			}
			if (abilityToUse != null) {
				trainedUnit.order(game,
						new COrderTargetWidget(abilityToUse.getHandleId(), rallyOrderId, target.getHandleId(), false),
						false);
			}
			return null;
		}

		@Override
		public Void accept(final CDestructable target) {
			return acceptWidget(this.game, this.trainedUnit, this.rallyOrderId, target);
		}

		@Override
		public Void accept(final CItem target) {
			return acceptWidget(this.game, this.trainedUnit, this.rallyOrderId, target);
		}
	}

	public int getFoodMade() {
		return this.foodMade;
	}

	public int getFoodUsed() {
		return this.foodUsed;
	}

	public int setFoodMade(final int foodMade) {
		final int delta = foodMade - this.foodMade;
		this.foodMade = foodMade;
		return delta;
	}

	public int setFoodUsed(final int foodUsed) {
		final int delta = foodUsed - this.foodUsed;
		this.foodUsed = foodUsed;
		return delta;
	}

	public void setDefaultBehavior(final CBehavior defaultBehavior) {
		this.defaultBehavior = defaultBehavior;
	}

	public CAbilityGoldMine getGoldMineData() {
		for (final CAbility ability : this.abilities) {
			if (ability instanceof CAbilityGoldMine) {
				return ((CAbilityGoldMine) ability);
			}
		}
		return null;
	}

	public int getGold() {
		for (final CAbility ability : this.abilities) {
			if (ability instanceof CAbilityGoldMine) {
				return ((CAbilityGoldMine) ability).getGold();
			}
			if (ability instanceof CAbilityBlightedGoldMine) {
				return ((CAbilityBlightedGoldMine) ability).getGold();
			}
		}
		return 0;
	}

	public void setGold(final int goldAmount) {
		for (final CAbility ability : this.abilities) {
			if (ability instanceof CAbilityGoldMine) {
				((CAbilityGoldMine) ability).setGold(goldAmount);
			}
			if (ability instanceof CAbilityBlightedGoldMine) {
				((CAbilityBlightedGoldMine) ability).setGold(goldAmount);
			}
		}
	}

	public Queue<COrder> getOrderQueue() {
		return this.orderQueue;
	}

	public COrder getCurrentOrder() {
		return this.lastStartedOrder;
	}

	public CAbilityHero getHeroData() {
		for (final CAbility ability : this.abilities) {
			if (ability instanceof CAbilityHero) {
				return (CAbilityHero) ability;
			}
		}
		return null;
	}

	public CAbilityInventory getInventoryData() {
		for (final CAbility ability : this.abilities) {
			if (ability instanceof CAbilityInventory) {
				return (CAbilityInventory) ability;
			}
		}
		return null;
	}

	public CAbilityCargoHold getCargoData() {
		for (final CAbility ability : this.abilities) {
			if (ability instanceof CAbilityCargoHold) {
				return (CAbilityCargoHold) ability;
			}
		}
		return null;
	}

	public void setUnitSpecificAttacks(final List<CUnitAttack> unitSpecificAttacks) {
		this.unitSpecificAttacks = unitSpecificAttacks;
	}

	public List<CUnitAttack> getUnitSpecificAttacks() {
		return this.unitSpecificAttacks;
	}

	public List<CUnitAttack> getAttacks() {
		if (this.unitSpecificAttacks != null) {
			return this.unitSpecificAttacks;
		}
		return this.unitType.getAttacks();
	}

	public void onPickUpItem(final CSimulation game, final CItem item, final boolean playUserUISounds) {
		this.stateNotifier.inventoryChanged();
		if (playUserUISounds) {
			game.unitPickUpItemEvent(this, item);
		}
	}

	public void onDropItem(final CSimulation game, final CItem droppedItem, final boolean playUserUISounds) {
		this.stateNotifier.inventoryChanged();
		if (playUserUISounds) {
			game.unitDropItemEvent(this, droppedItem);
		}
	}

	public boolean isInRegion(final CRegion region) {
		return this.containingRegions.contains(region);
	}

	@Override
	public float getMaxLife() {
		return this.maximumLife;
	}

	private static final class RegionCheckerImpl implements CRegionEnumFunction {
		private CUnit unit;
		private CRegionManager regionManager;

		public RegionCheckerImpl reset(final CUnit unit, final CRegionManager regionManager) {
			this.unit = unit;
			this.regionManager = regionManager;
			return this;
		}

		@Override
		public boolean call(final CRegion region) {
			if (this.unit.containingRegions.add(region)) {
				if (!this.unit.priorContainingRegions.contains(region)) {
					this.regionManager.onUnitEnterRegion(this.unit, region);
				}
			}
			return false;
		}

	}

	public boolean isBuilding() {
		return this.unitType.isBuilding();
	}

	public void onRemove(final CSimulation simulation) {
		final CPlayer player = simulation.getPlayer(this.playerIndex);
		if (WarsmashConstants.FIRE_DEATH_EVENTS_ON_REMOVEUNIT) {
			// Firing userspace triggers here causes items to appear around the player bases
			// in melee games.
			// (See "Remove creeps and critters from used start locations" implementation)
			setLife(simulation, 0);
		}
		else {
			if (this.constructing) {
				player.removeTechtreeInProgress(this.unitType.getTypeId());
			}
			else {
				player.removeTechtreeUnlocked(this.unitType.getTypeId());
			}
			setHidden(true);
			// setting hidden to let things that refer to this before it gets garbage
			// collected see it as basically worthless
		}
		simulation.getWorldCollision().removeUnit(this);
	}

	private static enum StateListenerUpdateType {
		ADD, REMOVE;
	}

	private static final class StateListenerUpdate {
		private final CUnitStateListener listener;
		private final StateListenerUpdateType updateType;

		public StateListenerUpdate(final CUnitStateListener listener, final StateListenerUpdateType updateType) {
			this.listener = listener;
			this.updateType = updateType;
		}

		public CUnitStateListener getListener() {
			return this.listener;
		}

		public StateListenerUpdateType getUpdateType() {
			return this.updateType;
		}
	}

	public void cancelUpgrade(final CSimulation game) {
		final CPlayer player = game.getPlayer(this.playerIndex);
		player.setUnitFoodUsed(this, this.unitType.getFoodUsed());
		int goldCost, lumberCost;
		final CUnitType newUpgradeUnitType = game.getUnitData().getUnitType(this.upgradeIdType);
		if (game.getGameplayConstants().isRelativeUpgradeCosts()) {
			goldCost = newUpgradeUnitType.getGoldCost() - this.unitType.getGoldCost();
			lumberCost = newUpgradeUnitType.getLumberCost() - this.unitType.getLumberCost();
		}
		else {
			goldCost = newUpgradeUnitType.getGoldCost();
			lumberCost = newUpgradeUnitType.getLumberCost();
		}
		player.refund(goldCost, lumberCost);

		final Iterator<CAbility> abilityIterator = this.abilities.iterator();
		while (abilityIterator.hasNext()) {
			final CAbility ability = abilityIterator.next();
			if (ability instanceof CAbilityBuildInProgress) {
				abilityIterator.remove();
			}
			else {
				ability.setDisabled(false);
				ability.setIconShowing(true);
			}
		}

		game.unitCancelUpgradingEvent(this, this.upgradeIdType);
		this.upgradeIdType = null;
		this.constructing = false;
		this.constructionProgress = 0;
		this.unitAnimationListener.playAnimation(true, PrimaryTag.STAND, SequenceUtils.EMPTY, 0.0f, true);
	}

	public void beginUpgrade(final CSimulation game, final War3ID rawcode) {
		this.upgradeIdType = rawcode;
		this.constructing = true;
		this.constructionProgress = 0;

		final CPlayer player = game.getPlayer(this.playerIndex);
		final CUnitType newUpgradeUnitType = game.getUnitData().getUnitType(rawcode);
		player.setUnitFoodUsed(this, newUpgradeUnitType.getFoodUsed());
		int goldCost, lumberCost;
		if (game.getGameplayConstants().isRelativeUpgradeCosts()) {
			goldCost = newUpgradeUnitType.getGoldCost() - this.unitType.getGoldCost();
			lumberCost = newUpgradeUnitType.getLumberCost() - this.unitType.getLumberCost();
		}
		else {
			goldCost = newUpgradeUnitType.getGoldCost();
			lumberCost = newUpgradeUnitType.getLumberCost();
		}
		player.charge(goldCost, lumberCost);
		add(game, new CAbilityBuildInProgress(game.getHandleIdAllocator().createId()));
		for (final CAbility ability : getAbilities()) {
			ability.visit(AbilityDisableWhileUpgradingVisitor.INSTANCE);
		}
		player.addTechtreeInProgress(rawcode);

		game.unitUpgradingEvent(this, rawcode);
		this.unitAnimationListener.playAnimation(true, PrimaryTag.BIRTH, SequenceUtils.EMPTY, 0.0f, true);
	}

	public void setUnitState(final CSimulation game, final CUnitState whichUnitState, final float value) {
		switch (whichUnitState) {
		case LIFE:
			setLife(game, value);
			break;
		case MANA:
			setMana(value);
			break;
		case MAX_LIFE:
			setMaximumLife((int) value);
			break;
		case MAX_MANA:
			setMaximumMana((int) value);
			break;
		}
	}

	public float getUnitState(final CSimulation game, final CUnitState whichUnitState) {
		switch (whichUnitState) {
		case LIFE:
			return getLife();
		case MANA:
			return getMana();
		case MAX_LIFE:
			return getMaximumLife();
		case MAX_MANA:
			return getMaximumMana();
		}
		return 0;
	}

	public boolean isUnitType(final CUnitTypeJass whichUnitType) {
		switch (whichUnitType) {
		case HERO:
			return getHeroData() != null;
		case DEAD:
			return isDead();
		case STRUCTURE:
			return isBuilding();

		case FLYING:
			return getUnitType().getTargetedAs().contains(CTargetType.AIR);
		case GROUND:
			return getUnitType().getTargetedAs().contains(CTargetType.GROUND);

		case ATTACKS_FLYING:
			for (final CUnitAttack attack : getAttacks()) {
				if (attack.getTargetsAllowed().contains(CTargetType.AIR)) {
					return true;
				}
			}
			return false;
		case ATTACKS_GROUND:
			for (final CUnitAttack attack : getAttacks()) {
				if (attack.getTargetsAllowed().contains(CTargetType.GROUND)) {
					return true;
				}
			}
			return false;

		case MELEE_ATTACKER:
			boolean hasAttacks = false;
			for (final CUnitAttack attack : getAttacks()) {
				if (attack.getWeaponType() != CWeaponType.NORMAL) {
					return false;
				}
				hasAttacks = true;
			}
			return hasAttacks;

		case RANGED_ATTACKER:
			for (final CUnitAttack attack : getAttacks()) {
				if (attack.getWeaponType() != CWeaponType.NORMAL) {
					return true;
				}
			}
			return false;

		case GIANT:
			return getUnitType().getClassifications().contains(CUnitClassification.GIANT);
		case SUMMONED:
			return getUnitType().getClassifications().contains(CUnitClassification.SUMMONED);
		case STUNNED:
			return getCurrentBehavior().getHighlightOrderId() == OrderIds.stunned;
		case PLAGUED:
			throw new UnsupportedOperationException(
					"cannot ask engine if unit is plagued: plague is not yet implemented");
		case SNARED:
			throw new UnsupportedOperationException(
					"cannot ask engine if unit is snared: snare is not yet implemented");

		case UNDEAD:
			return getUnitType().getClassifications().contains(CUnitClassification.UNDEAD);
		case MECHANICAL:
			return getUnitType().getClassifications().contains(CUnitClassification.MECHANICAL);
		case PEON:
			return getUnitType().getClassifications().contains(CUnitClassification.PEON);
		case SAPPER:
			return getUnitType().getClassifications().contains(CUnitClassification.SAPPER);
		case TOWNHALL:
			return getUnitType().getClassifications().contains(CUnitClassification.TOWNHALL);
		case ANCIENT:
			return this.unitType.getClassifications().contains(CUnitClassification.ANCIENT);

		case TAUREN:
			return getUnitType().getClassifications().contains(CUnitClassification.TAUREN);
		case POISONED:
			throw new UnsupportedOperationException(
					"cannot ask engine if unit is poisoned: poison is not yet implemented");
		case POLYMORPHED:
			throw new UnsupportedOperationException(
					"cannot ask engine if unit is POLYMORPHED: POLYMORPHED is not yet implemented");
		case SLEEPING:
			throw new UnsupportedOperationException(
					"cannot ask engine if unit is SLEEPING: SLEEPING is not yet implemented");
		case RESISTANT:
			throw new UnsupportedOperationException(
					"cannot ask engine if unit is RESISTANT: RESISTANT is not yet implemented");
		case ETHEREAL:
			throw new UnsupportedOperationException(
					"cannot ask engine if unit is ETHEREAL: ETHEREAL is not yet implemented");
		case MAGIC_IMMUNE:
			throw new UnsupportedOperationException(
					"cannot ask engine if unit is MAGIC_IMMUNE: MAGIC_IMMUNE is not yet implemented");

		}
		return false;
	}

	public int getTriggerEditorCustomValue() {
		return this.triggerEditorCustomValue;
	}

	public void setTriggerEditorCustomValue(final int triggerEditorCustomValue) {
		this.triggerEditorCustomValue = triggerEditorCustomValue;
	}

	public static String maybeMeaningfulName(final CUnit unit) {
		if (unit == null) {
			return "null";
		}
		return unit.getUnitType().getName();
	}

	public void fireCooldownsChangedEvent() {
		this.stateNotifier.ordersChanged();
	}

	public int getAbilityLevel(final War3ID abilityId) {
		final CLevelingAbility ability = getAbility(GetAbilityByRawcodeVisitor.getInstance().reset(abilityId));
		if (ability == null) {
			return 0;
		}
		else {
			return ability.getLevel();
		}
	}

	public boolean chargeMana(final int manaCost) {
		if (this.mana >= manaCost) {
			setMana(this.mana - manaCost);
			return true;
		}
		return false;
	}
}
