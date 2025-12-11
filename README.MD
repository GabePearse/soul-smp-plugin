# SoulSMP
A team-based Survival Multiplayer enhancement plugin introducing land-claim banners, shared team lives, beacon-style territory effects, dimensional anchoring, and a progression-based vault and upgrade system.

SoulSMP is designed for Paper/Spigot servers and emphasizes progression, cooperation, territory control, and meaningful player death consequences.

---

## ✨ Features Overview

### 🛡️ Team System  
- Create and manage teams (max 3 players by default)  
- Invitation-based joining  
- Team-specific chat and whisper  
- Persistent team progression data  
- Shared team lives  

### 🚩 Banner Claims  
- First banner defines team banner design  
- Claim radius centered on banner  
- Claim protections: block break/place, explosions, pistons, containers  
- Multi-dimension support  

### ⭐ Soul Tokens  
- Dropped on death  
- Used for upgrades  
- NBT protected  
- Cannot be crafted or renamed  

### 🧰 Team Vault  
- Expandable shared storage  
- Locked slots until upgraded  
- Vault GUI includes a Shop button  

### 💎 Upgrade Shop  
Purchase upgrades using Soul Tokens:
- Claim radius  
- Extra lives  
- Vault expansion  
- Dimensional banner unlocks  
- Dimensional teleport unlocks  
- Beacon effect upgrades  

### 🔥 Beacon Effects  
- Apply passive buffs inside claimed territory  
- Configurable effects and cost  
- Effects scale with upgrade levels  

---

## 🛠 Installation

1. Drop the plugin .jar into your `/plugins` folder  
2. Start the server to generate configs  
3. Modify `config.yml`, `effects.yml`, `shop.yml`, and `teams.yml` as needed  
4. Run `/soulsmp reload` to apply configuration changes  

---

## 📂 Documentation

Full technical documentation is available in:

```
/docs
  DeveloperGuide.md
  Architecture.md
  Commands.md
  Config.md
```

---

## 📝 License  
Add your chosen license here.

---

## 🤝 Contributing  
PRs are welcome! Submit improvements, bug fixes, and enhancements.

