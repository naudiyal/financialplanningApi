# Diagnosing and Resolving AWS RDS PostgreSQL Connection Issues from DBeaver (Local Machine)

This guide helps you troubleshoot and resolve issues when connecting to an AWS RDS PostgreSQL instance from DBeaver on your local machine.

---

## 1. Gather Required Information
- **RDS Endpoint**: Found in AWS RDS Console → Databases → [your DB] → Connectivity & security.
- **Port**: Default is 5432.
- **Database Name**: As configured (e.g., `financial_planning`).
- **Username/Password**: As set during RDS creation.

---

## 2. DBeaver Connection Setup
- **Host**: Use only the endpoint (e.g., `your-db.c6lmucoisg8z.us-east-1.rds.amazonaws.com`)
- **Port**: 5432
- **Database**: financial_planning
- **Username**: (your DB username)
- **Password**: (your DB password)

---

## 3. Common Connection Issues & Solutions

### A. Connection Timeout
- **Cause**: Network/firewall/security group blocks.
- **Fixes**:
  1. **RDS Security Group**: Inbound rule must allow your public IP on port 5432.
      - Type: PostgreSQL
      - Port: 5432
      - Source: Your public IP (e.g., `99.154.197.32/32`)
  2. **Check Your Public IP**: Visit https://checkip.amazonaws.com/ and update the rule if your IP changed.
  3. **RDS Public Accessibility**: Must be set to "Yes" in AWS RDS Console.
  4. **Local Firewall**: Ensure outbound connections on port 5432 are allowed.

### B. Invalid Host/Port/Database
- **Cause**: Typo or wrong field in DBeaver.
- **Fix**: Double-check endpoint, port, and database name. Do not include port or database in the host field.

### C. Authentication Failed
- **Cause**: Wrong username or password.
- **Fix**: Verify credentials in AWS RDS Console.

---

## 4. Step-by-Step Troubleshooting

1. **Test with psql (optional)**
   ```
   psql -h [endpoint] -U [username] -d [database] -p 5432
   ```
   - If this fails with timeout, it's a network/security group issue.

2. **Check Security Group Rules**
   - AWS Console → EC2 → Security Groups → [your group]
   - Inbound rule: PostgreSQL, port 5432, source = your public IP/32

3. **Check RDS Public Accessibility**
   - AWS Console → RDS → Databases → [your DB] → Connectivity & security
   - "Publicly accessible" must be Yes

4. **Test Port Reachability (Windows PowerShell)**
   ```
   Test-NetConnection [endpoint] -Port 5432
   ```
   - If not reachable, check security group and public accessibility.

5. **Reboot RDS Instance**
   - Sometimes required after changing settings.

---

## 5. Security Reminder
- Only allow your current IP in the security group (not 0.0.0.0/0) for production.
- Remove temporary open rules after testing.

---

## 6. Useful Links
- [AWS RDS Security Groups](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Overview.RDSSecurityGroups.html)
- [DBeaver Documentation](https://dbeaver.io/docs/)

---

## 7. Example Error Messages
- `Connection attempt timed out` → Network/security group issue.
- `FATAL: password authentication failed` → Wrong username/password.
- `could not translate host name` → Typo in endpoint.

---

## 8. Still Not Working?
- Double-check all settings above.
- Try connecting from a different network.
- Contact AWS support if all else fails.
