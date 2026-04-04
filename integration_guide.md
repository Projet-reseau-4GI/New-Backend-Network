# Guide d'Intégration pour Plateformes Externes

Ce guide explique comment intégrer le service d'analyse de documents VerifID dans votre propre système.

## 1. Obtention de votre Clé d'API

Pour utiliser nos services, vous devez d'abord obtenir une clé d'API unique pour votre plateforme.

1.  **Demande d'accès** : Contactez l'administrateur pour enregistrer votre organisation avec un email.
2.  **Connexion par OTP** :
    *   Appelez `POST /api/auth/otp/request` avec votre email.
    *   Saisissez le code reçu par email via `POST /api/auth/otp/verify`.
3.  **Récupération de la clé** : La réponse de vérification contient votre `apiKey`. Conservez-la précieusement !

---

## 2. Appel de l'API d'Analyse

Toutes les requêtes vers nos services d'analyse doivent inclure votre clé dans le header HTTP `X-API-KEY`.

### Point de terminaison
`POST /api/documents/upload-analyze`

### Authentification
Header : `X-API-KEY: <votre_cle_api>`

---

## 3. Exemples d'Intégration

### cURL
```bash
curl -X POST https://api.verifid.com/api/documents/upload-analyze \
  -H "X-API-KEY: sk_platform_12345" \
  -F "frontFile=@/chemin/vers/cni_recto.jpg" \
  -F "backFile=@/chemin/vers/cni_verso.jpg" \
  -F "pieceType=ID_CARD"
```

### Node.js (axios)
```javascript
const axios = require('axios');
const FormData = require('form-data');
const fs = require('fs');

async function analyzeDocument() {
  const form = new FormData();
  form.append('frontFile', fs.createReadStream('cni.jpg'));
  form.append('pieceType', 'ID_CARD');

  const response = await axios.post('https://api.verifid.com/api/documents/upload-analyze', form, {
    headers: {
      ...form.getHeaders(),
      'X-API-KEY': 'votre_cle_api'
    }
  });

  console.log(response.data);
}
```

### Python (requests)
```python
import requests

url = "https://api.verifid.com/api/documents/upload-analyze"
headers = {"X-API-KEY": "votre_cle_api"}
files = {
    "frontFile": open("id_front.png", "rb"),
    "pieceType": (None, "ID_CARD")
}

response = requests.post(url, headers=headers, files=files)
print(response.json())
```

---

## 4. Format de Réponse

L'API retourne un objet JSON contenant les informations extraites et le statut de validité :

```json
{
  "documentType": "ID_CARD",
  "documentNumber": "123456789",
  "holderName": "JOHN DOE",
  "dateOfBirth": "1990-01-01",
  "expirationDate": "2030-12-31",
  "isValid": true,
  "confidenceScore": 0.95,
  "validationMessage": "Document valide"
}
```
