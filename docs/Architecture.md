# SoulSMP — Architecture Overview

The plugin uses a subsystem-based architecture allowing modular expansion.

---

# 🧩 System Diagram (High-Level)

```
+--------------------+
|     SoulSMP.java   |
+--------------------+
          |
          v
+-------------------------+
|      TeamManager        |
+-------------------------+
   /       |          \
  v        v           v
Claims   Vaults      Upgrades
  |        |            |
  v        v            v
Listeners GUIs      Token System
```

---

# 🔍 Components

## TeamManager
- Central data registry  
- Stores teams in memory  
- Loads/saves YAML files  

## BannerListener
- Territory enforcement  
- Banner registration  
- Block/entity protection  

## Vault System
- Base64 inventory  
- GUI management  
- Locked slot logic  

## Shop System
- GUI builders  
- Upgrade cost logic  
- Token deduction  

## Beacon Effects Engine
- Scheduled repeating task  
- Applies effects inside claims  

---

# 📡 Data Flow Example: Upgrade Purchase

```
Click upgrade item
  ↓
Validate tokens
  ↓
TeamManager.update(team)
  ↓
TeamBannerShopGui.refresh()
  ↓
Broadcast or update UI
```

---

# 🗃 File Layout

```
src/main/java/.../team
src/main/java/.../listeners
src/main/java/.../vault
src/main/java/.../shop
src/main/java/.../upgrades
src/main/java/.../tokens
src/main/resources/*.yml
```

---

# ⚙ Integration Notes

- All logic assumes Paper/Spigot API  
- No asynchronous modification of Bukkit objects  
- Use dependency injection via plugin main class  

