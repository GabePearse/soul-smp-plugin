# SoulSMP — Developer Guide
This guide provides a deep technical breakdown of the plugin architecture, subsystem design, event flow, and responsibilities. It is intended for contributors or developers extending the system.

---

# 📦 Core Subsystems

## 1. Team System
Handles:
- Membership  
- Invitations  
- Persistent team data  
- Owner transfers  
- Team lifecycle events  

Core classes:
- `Team.java`
- `TeamManager.java`
- `TeamCommand.java`

Teams store:
- Members  
- Owner  
- Banner design  
- Banner locations  
- Claim radius  
- Vault size  
- Effect levels  
- Lives  
- Dimensional unlocks  

---

## 2. Banner Claim System
Implements land ownership and interaction restrictions.

Main responsibilities:
- Validate banner design  
- Register banners per dimension  
- Enforce claim protections  
- Handle banner removal  

Important logic in `BannerListener.java`:
- Block interactions  
- Entity interactions  
- Explosion cancellation  
- Piston movement interception  
- Fluid flow prevention  

Claim definition:
```
center = banner block location
radius = team.claimRadius
dimension = world UUID
```

---

## 3. Team Lives & Death Handling
When a player dies:
1. Drop a Soul Token  
2. Reduce team lives  
3. Trigger shatter if lives reach 0  

Shatter event:
- Claim radius set to 0  
- Banner coords broadcast  
- Team effectively disabled  

Handled in:
- `PlayerDeathListener.java`
- `TeamManager.java`

---

## 4. Soul Token Economy
Tokens are NBT-tagged Nether Stars.

Token rules:
- Dropped on death  
- Used for all upgrades  
- Cannot be crafted or renamed  
- Only valid tokens allowed  

Handled in:
- `SoulTokenManager.java`
- `SoulTokenProtectionListener.java`

---

## 5. Team Vault System
Features:
- 27-slot GUI  
- Locked and unlocked slots  
- Slot 26 = Shop button  
- Saved via Base64  

Core classes:
- `TeamVaultManager`
- `TeamVaultListener`
- `InventoryUtils`

---

## 6. Upgrade Shop
Categories:
- Radius upgrades  
- Vault expansion  
- Extra lives  
- Beacon effect upgrades  
- Dimensional banner unlocks  
- Teleport unlocks  

Files:
- `TeamBannerShopGui.java`
- `TeamBannerShopListener.java`
- `BannerShopItem.java`
- `shop.yml`

---

## 7. Beacon Effect Engine
Effects applied every second to players standing in their own claim.

Effects include:
- Speed  
- Haste  
- Strength  
- Resistance  
- Jump Boost  
- Regeneration  

Controlled by:
- `BeaconEffectManager`
- `BeaconEffectsGui`
- `effects.yml`

---

## 8. Dimensional Banner Mechanics
Teams may unlock:
- Nether banner  
- End banner  
- Dimensional teleportation  

Stored fields:
```
unlockedDimensions
bannerLocations
unlockedTeleports
```

---

# 🔁 Event Flow Summary

## Banner Placement
```
Player places banner →
Validate pattern →
Check dimension unlock →
Save banner location →
Apply claim center
```

## Player Death
```
Drop token →
team.lives-- →
if lives == 0: shatter()
```

## Shop Usage
```
Click shop slot →
Open GUI →
Check token cost →
Upgrade team →
Save team →
Refresh GUI
```

---

# 🧱 Subsystem Responsibilities Chart

| Subsystem | Location | Description |
|----------|----------|-------------|
| Teams | team/ | Data model + lifecycle |
| Claims | listeners/BannerListener | Protections + banner logic |
| Tokens | tokens/ | Creation + validation |
| Vault | vault/ | GUI + persistence |
| Upgrades | shop/, upgrades/ | Shop GUIs + effect logic |

---

# 🧪 Testing Checklist

- Banner matching with many patterns  
- Vault saving/loading  
- Beacon effects after dimension teleport  
- Shatter event correctness  
- GUI slot restrictions  
- Token validation  

---

# 🛠 Developer Notes
- Avoid expensive loops in listeners  
- Use squared distance for radius checks  
- Maintain thread safety: no async Bukkit API calls  
- Ensure config reloads don’t duplicate listeners  

