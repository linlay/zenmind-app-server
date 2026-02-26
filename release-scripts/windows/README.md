# windows scripts

Put all Windows scripts in this folder:

- PowerShell scripts: `*.ps1`
- Batch scripts: `*.bat`

Current scripts:

- `setup-jwk-public-key.ps1`

Example:

```powershell
.\release-scripts\windows\setup-jwk-public-key.ps1 -Mode bootstrap -DbPath .\data\auth.db -OutDir .\data\keys -PublicOut .\data\keys\publicKey.pem
```
