# SoulSMP — Configuration Reference

This file explains the YAML configs used by the plugin.

---

# 1. `config.yml`
General plugin settings.

Example:
```yaml
maxTeamSize: 3
defaultLives: 5
defaultRadius: 1
```

---

# 2. `teams.yml`
Auto-generated.  
Stores:
- Members  
- Banner design  
- Banner locations  
- Vault size  
- Effect levels  
- Lives  
- Dimensional unlocks  

Developers should not manually modify.

---

# 3. `effects.yml`
Defines beacon effect upgrades.

Example:
```yaml
speed:
  display: "Speed"
  levels:
    1: { cost: 3 }
    2: { cost: 7 }
```

---

# 4. `shop.yml`
Controls shop item layout and costs.

Example:
```yaml
radius:
  baseCost: 5
  multiplier: 1.5
```

---

# 5. Vault Persistence
Vaults are stored via Base64 strings:

```
vault:
  "0": "BASE64_HERE"
```

Handled automatically by `InventoryUtils`.

