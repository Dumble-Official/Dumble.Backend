#!/usr/bin/env python3
"""Generate the Dumble Backend Postman v2.1 collection + environments + README."""
import json, os, re

OUT = r"c:/Users/youss/Desktop/Dumble.Backend/postman"
os.makedirs(OUT, exist_ok=True)

# ---------- builders ----------
def url_obj(path, query):
    # path like "/api/posts/{{post_id}}"
    raw_path = path.split("?")[0]
    segs = [s for s in raw_path.split("/") if s != ""]
    q = []
    for item in (query or []):
        k, v = item[0], item[1]
        d = item[2] if len(item) > 2 else None
        e = {"key": k, "value": v}
        if d: e["description"] = d
        q.append(e)
    raw = "{{base_url}}" + raw_path
    if q:
        raw += "?" + "&".join(f"{e['key']}={e['value']}" for e in q)
    o = {"raw": raw, "host": ["{{base_url}}"], "path": segs}
    if q: o["query"] = q
    return o

def auto_tests(method, save):
    lines = [
        "pm.test(\"Status is a success code\", function () {",
        "    pm.expect(pm.response.code).to.be.oneOf([200, 201, 202, 204]);",
        "});",
    ]
    for var, expr in (save or []):
        lines += [
            "try {",
            "    var _j = pm.response.json();",
            f"    if ({expr} !== undefined && {expr} !== null) pm.collectionVariables.set(\"{var}\", {expr});",
            "} catch (e) {}",
        ]
    return lines

def R(name, method, path, body=None, form=None, q=None, public=False, role=None,
      tests=None, save=None, examples=None, desc=None, headers=None, idem=False):
    req = {"method": method, "header": list(headers or [])}
    notes = []
    if role: notes.append(f"**Auth:** requires role `{role}`.")
    if public: notes.append("**Auth:** public (no token required).")
    if idem:
        req["header"].append({"key": "Idempotency-Key", "value": "{{$guid}}", "description": "Unique per logical request; replays return the original result."})
    if desc: notes.append(desc)
    if body is not None:
        req["header"].append({"key": "Content-Type", "value": "application/json"})
        req["body"] = {"mode": "raw", "raw": json.dumps(body, indent=2),
                       "options": {"raw": {"language": "json"}}}
    elif form is not None:
        fd = []
        for k, v, t in form:
            entry = {"key": k, "type": t}
            if t == "file":
                entry["src"] = []
            else:
                entry["value"] = v
            fd.append(entry)
        req["body"] = {"mode": "formdata", "formdata": fd}
    req["url"] = url_obj(path, q)
    if public:
        req["auth"] = {"type": "noauth"}
    item = {"name": name, "request": req,
            "event": [{"listen": "test", "script": {"type": "text/javascript",
                       "exec": tests if tests is not None else auto_tests(method, save)}}]}
    if notes:
        item["request"]["description"] = "\n\n".join(notes)
    if examples:
        item["response"] = []
        for ex in examples:
            item["response"].append({
                "name": ex["name"],
                "originalRequest": {"method": method, "header": [], "url": url_obj(path, q),
                                    **({"body": req.get("body")} if "body" in req else {})},
                "status": ex.get("status_text", "OK"),
                "code": ex.get("code", 200),
                "_postman_previewlanguage": "json",
                "header": [{"key": "Content-Type", "value": "application/json"}],
                "body": json.dumps(ex["body"], indent=2),
            })
    return item

def F(name, items, desc=None):
    f = {"name": name, "item": items}
    if desc: f["description"] = desc
    return f

# convenience example
def ex(name, code, body, status_text="OK"):
    return {"name": name, "code": code, "status_text": status_text, "body": body}

UUID = "550e8400-e29b-41d4-a716-446655440000"

# ======================================================================
# FOLDERS
# ======================================================================
folders = []

# ---- 1. Auth ----
folders.append(F("01 · Auth", [
    R("Register", "POST", "/api/auth/register", public=True,
      body={"firstName":"Ada","lastName":"Lovelace","email":"ada+{{$timestamp}}@dumble.test","password":"Passw0rd!23"},
      save=[("auth_token","_j.accessToken"),("refresh_token","_j.refreshToken"),("user_id","_j.user.id")],
      examples=[ex("201 Created",201,{"accessToken":"eyJ...","refreshToken":"a0a2...","tokenType":"Bearer",
                  "user":{"id":UUID,"email":"ada@dumble.test","firstName":"Ada","lastName":"Lovelace","userType":"PARTICIPANT"}},"Created")],
      desc="Creates a participant account and returns a JWT. The test script stores `auth_token`, `refresh_token`, `user_id`."),
    R("Login", "POST", "/api/auth/login", public=True,
      body={"email":"admin@dumble.local","password":"{{admin_password}}"},
      tests=[
        "pm.test(\"Status is 200\", () => pm.response.to.have.status(200));",
        "var j = pm.response.json();",
        "pm.test(\"Returns an access token\", () => pm.expect(j.accessToken).to.be.a('string'));",
        "pm.collectionVariables.set(\"auth_token\", j.accessToken);",
        "pm.collectionVariables.set(\"refresh_token\", j.refreshToken);",
        "pm.collectionVariables.set(\"user_id\", j.user.id);",
      ],
      examples=[ex("200 OK",200,{"accessToken":"eyJ...","refreshToken":"a0a2...","tokenType":"Bearer",
                  "user":{"id":UUID,"email":"admin@dumble.local","userType":"ADMIN"}})],
      desc="Authenticates and stores the JWT in `auth_token` (used by every other request via collection-level Bearer auth)."),
    R("Refresh token", "POST", "/api/auth/refresh", public=True,
      body={"refreshToken":"{{refresh_token}}"},
      save=[("auth_token","_j.accessToken"),("refresh_token","_j.refreshToken")]),
    R("Logout", "POST", "/api/auth/logout", body={"refreshToken":"{{refresh_token}}"}),
    R("Change password", "POST", "/api/auth/change-password",
      body={"currentPassword":"Passw0rd!23","newPassword":"NewPassw0rd!45"}),
    R("Google sign-in", "POST", "/api/auth/google", public=True,
      body={"idToken":"<google-id-token>"}),
    R("Hub token (SignalR)", "POST", "/api/auth/hub-token",
      desc="Short-lived token for negotiating the chat/notification SignalR websocket."),
]))

# ---- 2. Users ----
folders.append(F("02 · Users & Profile", [
    R("Get my profile", "GET", "/api/users/me",
      examples=[ex("200 OK",200,{"id":UUID,"email":"ada@dumble.test","firstName":"Ada","lastName":"Lovelace",
                  "displayName":None,"userType":"PARTICIPANT","weight":None})]),
    R("Onboarding", "PATCH", "/api/users/me/onboarding",
      body={"dateOfBirth":"1990-05-15","gender":"MALE","weight":75.5,"height":180.0,"fitnessGoals":["strength","endurance"]}),
    R("Update my profile", "PATCH", "/api/users/me",
      body={"weight":76.0,"injuries":"none","fitnessGoals":["strength"],"displayName":"Ada L","pfp":"https://img/ada.jpg","bio":"Fitness fan"}),
    R("Delete my account (RTBF)", "DELETE", "/api/users/me",
      desc="Right-to-be-forgotten: hard-deletes the account and cascades a purge across services. Irreversible."),
    R("Submit role request", "POST", "/api/users/me/role-requests",
      body={"requestedRole":"TRAINER","certificateUrl":"https://img/cert.pdf","note":"5 years experience"},
      save=[("role_request_id","_j.id")]),
    R("My role requests", "GET", "/api/users/me/role-requests"),
    R("Update my role request", "PATCH", "/api/users/me/role-requests/{{role_request_id}}",
      body={"requestedRole":"TRAINER","certificateUrl":"https://img/cert2.pdf","note":"updated"}),
    # admin
    R("[admin] Search users", "GET", "/api/users", role="ADMIN,MODERATOR",
      q=[("query","ada","name/email search"),("page","0"),("size","20")]),
    R("[admin] Get user by id", "GET", "/api/users/{{target_user_id}}", role="ADMIN,MODERATOR"),
    R("[admin] Ban user", "POST", "/api/users/{{target_user_id}}/ban", role="ADMIN,MODERATOR"),
    R("[admin] Unban user", "POST", "/api/users/{{target_user_id}}/unban", role="ADMIN,MODERATOR"),
    R("[admin] List banned users", "GET", "/api/users/banned", role="ADMIN,MODERATOR"),
]))

# ---- 3. Posts ----
folders.append(F("03 · Posts", [
    R("Create post (multipart)", "POST", "/api/posts",
      form=[("Content","Great workout today! #gains","text"),("Hashtags","gains","text"),
            ("GymId","","text"),("Images","","file")],
      save=[("post_id","_j.id")],
      examples=[ex("201 Created",201,{"id":UUID,"authorId":UUID,"authorDisplayName":"Ada L","authorType":"PARTICIPANT",
                  "content":"Great workout today! #gains","status":"Active","reactionsCount":0,"commentsCount":0,
                  "images":[],"hashtags":["gains"],"createdAt":"2026-06-18T10:00:00Z"},"Created")],
      desc="multipart/form-data. `Content` text, repeatable `Hashtags`, optional `Images` files."),
    R("Get post", "GET", "/api/posts/{{post_id}}", public=True),
    R("Update post", "PUT", "/api/posts/{{post_id}}", body={"content":"Edited caption #gains","hashtags":["gains","legday"]}),
    R("Delete post", "DELETE", "/api/posts/{{post_id}}"),
    R("Posts by user", "GET", "/api/posts/user/{{user_id}}", public=True, q=[("cursor",""),("limit","20")]),
    R("Posts by gym", "GET", "/api/posts/gym/{{gym_id}}", public=True, q=[("cursor",""),("limit","20")]),
    R("Posts by hashtag", "GET", "/api/posts/hashtag/gains", public=True, q=[("cursor",""),("limit","20")]),
    R("Search posts", "GET", "/api/posts/search", public=True, q=[("q","workout"),("cursor",""),("limit","20")]),
    R("Batch get posts", "POST", "/api/posts/batch", body={"ids":["{{post_id}}"]}, desc="Max 100 ids."),
    R("Post catalog (backend)", "GET", "/api/posts/catalog", q=[("cursor",""),("limit","100")],
      desc="Full non-deleted catalog; used by the recommendation reconcile."),
    R("[mod] Flag post", "POST", "/api/posts/{{post_id}}/flag", role="Moderator/Admin"),
    R("[mod] Unflag post", "POST", "/api/posts/{{post_id}}/unflag", role="Moderator/Admin"),
]))

# ---- 4. Comments ----
folders.append(F("04 · Comments", [
    R("Add comment", "POST", "/api/posts/{{post_id}}/comments",
      body={"content":"Nice work!","parentCommentId":None}, save=[("comment_id","_j.id")]),
    R("List comments", "GET", "/api/posts/{{post_id}}/comments", public=True, q=[("cursor",""),("limit","20")]),
    R("Get comment", "GET", "/api/comments/{{comment_id}}", public=True),
    R("Update comment", "PUT", "/api/comments/{{comment_id}}", body={"content":"Edited comment"}),
    R("Delete comment", "DELETE", "/api/comments/{{comment_id}}"),
    R("List replies", "GET", "/api/comments/{{comment_id}}/replies", public=True, q=[("cursor",""),("limit","20")]),
    R("Add comment reaction", "POST", "/api/comments/{{comment_id}}/reactions", body={"type":"like"}),
    R("Remove comment reaction", "DELETE", "/api/comments/{{comment_id}}/reactions"),
    R("List comment reactions", "GET", "/api/comments/{{comment_id}}/reactions/list", public=True, q=[("offset","0"),("limit","20")]),
    R("[mod] Flag comment", "POST", "/api/comments/{{comment_id}}/flag", role="Moderator/Admin"),
    R("[mod] Unflag comment", "POST", "/api/comments/{{comment_id}}/unflag", role="Moderator/Admin"),
]))

# ---- 5. Reactions & Hashtags ----
folders.append(F("05 · Reactions & Hashtags", [
    R("Add post reaction", "POST", "/api/posts/{{post_id}}/reactions", body={"type":"like"},
      desc="type: like | love | etc."),
    R("Remove post reaction", "DELETE", "/api/posts/{{post_id}}/reactions"),
    R("Get post reaction summary", "GET", "/api/posts/{{post_id}}/reactions", public=True),
    R("List post reactions", "GET", "/api/posts/{{post_id}}/reactions/list", public=True, q=[("offset","0"),("limit","20")]),
    R("Search hashtags", "GET", "/api/hashtags/search", public=True, q=[("q","gain"),("limit","20")]),
    R("Trending hashtags", "GET", "/api/hashtags/trending", public=True, q=[("limit","20")]),
]))

# ---- 6. Social ----
folders.append(F("06 · Social (Follows)", [
    R("Follow user", "POST", "/api/social/follow/{{target_user_id}}"),
    R("Unfollow user", "DELETE", "/api/social/follow/{{target_user_id}}"),
    R("Is following?", "GET", "/api/social/follow/{{target_user_id}}/status"),
    R("Follow-status batch", "POST", "/api/social/follow/status/batch", body={"userIds":["{{target_user_id}}"]}),
    R("Followers", "GET", "/api/social/followers/{{user_id}}", public=True, q=[("cursor",""),("limit","20")]),
    R("Following", "GET", "/api/social/following/{{user_id}}", public=True, q=[("cursor",""),("limit","20")]),
    R("Follow counts", "GET", "/api/social/{{user_id}}/counts", public=True),
]))

# ---- 7. Feed & Recommendations ----
folders.append(F("07 · Feed & Recommendations", [
    R("Home feed (Recombee)", "GET", "/api/feed/home", q=[("cursor",""),("limit","20")],
      examples=[ex("200 OK",200,{"items":[{"id":UUID,"authorId":UUID,"authorDisplayName":"Bob","content":"hi",
                  "images":[],"reactionsCount":3,"commentsCount":1,"createdAt":"2026-06-18T10:00:00Z"}],
                  "nextCursor":None,"hasMore":False})]),
    R("Explore feed (Recombee)", "GET", "/api/feed/explore", q=[("cursor",""),("limit","20")]),
    R("Home feed (social alias)", "GET", "/api/feed", q=[("cursor",""),("limit","20")],
      desc="Compatibility path; proxies to /api/feed/home."),
    R("Suggested users", "GET", "/api/feed/suggested-users", q=[("limit","10")]),
    R("Track behavior", "POST", "/api/feed/behavior",
      body={"postId":"{{post_id}}","eventType":"View","eventData":None},
      desc="eventType: View | Click | TimeSpent (eventData = dwell seconds for TimeSpent)."),
    R("Track behavior (batch)", "POST", "/api/feed/behavior/batch",
      body={"events":[{"postId":"{{post_id}}","eventType":"View","eventData":None},
                      {"postId":"{{post_id}}","eventType":"TimeSpent","eventData":"30"}]}),
]))

# ---- 8. Chat ----
folders.append(F("08 · Chat", [
    R("Create conversation", "POST", "/api/chat/conversations",
      body={"type":"Direct","name":None,"participantIds":["{{target_user_id}}"]},
      save=[("conversation_id","_j.id")],
      desc="type: Direct (1:1) or Group (set name)."),
    R("My conversations", "GET", "/api/chat/conversations", q=[("cursor",""),("limit","20")]),
    R("Get conversation", "GET", "/api/chat/conversations/{{conversation_id}}"),
    R("Update conversation (group)", "PUT", "/api/chat/conversations/{{conversation_id}}",
      body={"name":"Leg Day Crew","imageUrl":"https://img/group.jpg"}),
    R("Add participants", "POST", "/api/chat/conversations/{{conversation_id}}/participants",
      body={"userIds":["{{target_user_id}}"]}),
    R("Remove participant", "DELETE", "/api/chat/conversations/{{conversation_id}}/participants/{{target_user_id}}"),
    R("Set participant role", "PUT", "/api/chat/conversations/{{conversation_id}}/participants/{{target_user_id}}/role",
      body={"role":"Admin"}, desc="role: Admin | Member."),
    R("Leave conversation", "POST", "/api/chat/conversations/{{conversation_id}}/leave"),
    R("Send message", "POST", "/api/chat/conversations/{{conversation_id}}/messages",
      body={"content":"Hey!","replyToMessageId":None,"imageUrl":None}, save=[("message_id","_j.id")]),
    R("List messages", "GET", "/api/chat/conversations/{{conversation_id}}/messages", q=[("cursor",""),("limit","20")]),
    R("Edit message", "PUT", "/api/chat/messages/{{message_id}}", body={"content":"Hey there!"}),
    R("Delete message", "DELETE", "/api/chat/messages/{{message_id}}"),
    R("Mark conversation read", "PUT", "/api/chat/conversations/{{conversation_id}}/read", q=[("messageId","{{message_id}}")]),
    R("Block user", "POST", "/api/chat/blocks/{{target_user_id}}"),
    R("Unblock user", "DELETE", "/api/chat/blocks/{{target_user_id}}"),
    R("List blocked users", "GET", "/api/chat/blocks"),
]))

# ---- 9. Notifications ----
folders.append(F("09 · Notifications", [
    R("My notifications", "GET", "/api/notifications", q=[("cursor",""),("limit","20")]),
    R("Unread count", "GET", "/api/notifications/unread/count"),
    R("Mark notification read", "PUT", "/api/notifications/{{notification_id}}/read"),
    R("Mark all read", "PUT", "/api/notifications/read-all"),
    R("Delete notification", "DELETE", "/api/notifications/{{notification_id}}"),
    R("Get preferences", "GET", "/api/notifications/preferences"),
    R("Update preferences", "PUT", "/api/notifications/preferences",
      body={"preferences":{"MessageReceived":{"push":True,"inApp":True},"FriendRequest":{"push":False,"inApp":True}}}),
    R("Register device", "POST", "/api/notifications/devices", body={"token":"fcm-token-xyz","platform":"Android"}),
    R("List devices", "GET", "/api/notifications/devices"),
    R("Unregister device", "DELETE", "/api/notifications/devices/fcm-token-xyz"),
]))

# ---- 10. Gyms ----
folders.append(F("10 · Gyms", [
    R("Create gym", "POST", "/api/gyms/create", role="GYM_OWNER",
      body={"name":"Elite Fitness","bio":"Premium gym","address":"123 Fitness St, Cairo",
            "location":{"lat":30.0444,"lng":31.2357},"genderType":"UNISEX","email":"contact@elite.com",
            "phone":"+201234567890","licenseId":"LIC-001","openTime":"06:00:00","closeTime":"22:00:00","amenityIds":[1,2]},
      save=[("gym_id","_j.id")]),
    R("Get gym", "GET", "/api/gyms/{{gym_id}}"),
    R("Update gym", "PUT", "/api/gyms/update/{{gym_id}}",
      body={"name":"Elite Fitness Pro","bio":"Premium","address":"123 Fitness St","location":{"lat":30.0444,"lng":31.2357},
            "email":"contact@elite.com","phone":"+201234567890","openTime":"06:00:00","closeTime":"23:00:00"}),
    R("Delete gym", "DELETE", "/api/gyms/delete/{{gym_id}}"),
    R("List / search gyms", "GET", "/api/gyms",
      q=[("name","Elite"),("genderType","UNISEX"),("verified","true"),("status","ACTIVE"),("page","0"),("size","20")]),
    R("Nearby gyms", "GET", "/api/gyms/nearby", q=[("lat","30.0444"),("lng","31.2357"),("distance","5"),("page","0"),("size","10")]),
    R("Add staff", "POST", "/api/gyms/{{gym_id}}/staff/add", body={"userId":"{{target_user_id}}","role":"MODERATOR"}),
    R("List staff", "GET", "/api/gyms/{{gym_id}}/staff"),
    R("Update staff role", "PATCH", "/api/gyms/{{gym_id}}/staff/{{target_user_id}}/role", q=[("role","TRAINER")]),
    R("Remove staff", "DELETE", "/api/gyms/{{gym_id}}/staff/delete/{{target_user_id}}"),
    R("Upload gym image", "POST", "/api/gyms/{{gym_id}}/images/upload", q=[("type","GALLERY")], form=[("file","","file")]),
    R("List gym images", "GET", "/api/gyms/{{gym_id}}/images"),
    R("Images by type", "GET", "/api/gyms/{{gym_id}}/images/images-by-type", q=[("type","GALLERY")]),
    R("Delete gym image", "DELETE", "/api/gyms/{{gym_id}}/images/delete/1"),
    R("Upload gym document", "POST", "/api/gyms/{{gym_id}}/documents/upload", form=[("file","","file")]),
    R("List gym documents", "GET", "/api/gyms/{{gym_id}}/documents"),
]))

# ---- 11. Amenities ----
folders.append(F("11 · Amenities", [
    R("List amenities", "GET", "/api/amenities", q=[("page","0"),("size","20")]),
    R("Get amenity", "GET", "/api/amenities/1"),
    R("Search amenities", "GET", "/api/amenities/search", q=[("keyword","weights"),("page","0"),("size","20")]),
    R("[admin] Create amenity", "POST", "/api/amenities/add", role="ADMIN",
      body={"name":"Sauna","description":"Dry sauna","isActive":True}),
    R("[admin] Update amenity", "PUT", "/api/amenities/update/1", role="ADMIN",
      body={"name":"Free Weights","description":"Dumbbells","isActive":True}),
    R("[admin] Toggle amenity", "PATCH", "/api/amenities/toggle-amenity/1", role="ADMIN"),
    R("[admin] Delete amenity", "DELETE", "/api/amenities/delete/1", role="ADMIN"),
]))

# ---- 12. Gym Registrations ----
folders.append(F("12 · Gym Registrations", [
    R("Submit registration", "POST", "/api/gym-registrations", role="PARTICIPANT",
      body={"pageName":"Elite Network","nationalIdUrl":"https://img/id.pdf","commercialRegisterUrl":"https://img/reg.pdf",
            "taxCardUrl":"https://img/tax.pdf","note":"expanding",
            "branches":[{"name":"Downtown","bio":"main","address":"123 St","lat":30.0444,"lng":31.2357,
                         "genderType":"UNISEX","email":"d@elite.com","phone":"+2010","licenseId":"L-1",
                         "openTime":"06:00:00","closeTime":"22:00:00","premisesProofUrl":"https://img/p.pdf",
                         "operatingLicenseUrl":"https://img/o.pdf","civilDefenseUrl":"https://img/c.pdf"}]},
      save=[("registration_id","_j.id")]),
    R("My registrations", "GET", "/api/gym-registrations", role="PARTICIPANT"),
    R("Update registration", "PATCH", "/api/gym-registrations/{{registration_id}}", role="PARTICIPANT (owner)",
      body={"pageName":"Elite Network v2","branches":[]}, desc="Allowed only when status = CHANGES_REQUESTED."),
    R("[admin] List registrations", "GET", "/api/admin/gym-registrations", role="ADMIN",
      q=[("status","PENDING"),("page","0"),("size","20")]),
    R("[admin] Approve", "POST", "/api/admin/gym-registrations/{{registration_id}}/approve", role="ADMIN"),
    R("[admin] Request changes", "POST", "/api/admin/gym-registrations/{{registration_id}}/request-changes", role="ADMIN",
      body={"message":"Provide updated premises docs"}),
    R("[admin] Reject", "POST", "/api/admin/gym-registrations/{{registration_id}}/reject", role="ADMIN",
      body={"message":"Docs do not meet requirements"}),
    R("[admin] Verify gym", "POST", "/api/admin/gyms/{{gym_id}}/verify", role="ADMIN,MODERATOR"),
    R("[admin] Set gym status", "PATCH", "/api/admin/gyms/{{gym_id}}/status", role="ADMIN,MODERATOR", q=[("status","SUSPENDED")]),
]))

# ---- 13. Bundles & Categories ----
folders.append(F("13 · Bundles & Categories", [
    R("Browse bundles", "GET", "/api/bundles", public=True, q=[("pageIndex","1"),("pageSize","20")]),
    R("Get bundle", "GET", "/api/bundles/{{bundle_id}}", public=True),
    R("Create bundle (multipart)", "POST", "/api/bundles", role="GYM_OWNER/GYM/TRAINER",
      form=[("name","Premium Bundle","text"),("description","30-day access","text"),("price","99.99","text"),
            ("status","ACTIVE","text"),("expiresOn","2026-12-31T23:59:59Z","text"),
            ("categoryId","{{category_id}}","text"),("images","","file")],
      save=[("bundle_id","_j.id")]),
    R("Update bundle", "PUT", "/api/bundles/{{bundle_id}}", role="owner/ADMIN",
      body={"id":"{{bundle_id}}","name":"Premium+","description":"updated","price":119.99,"status":"ACTIVE",
            "expiresOn":"2026-12-31T23:59:59Z","categoryId":"{{category_id}}"}),
    R("Delete bundle", "DELETE", "/api/bundles/{{bundle_id}}", role="owner/ADMIN"),
    R("List categories", "GET", "/api/categories", public=True, save=[("category_id","_j[0] && _j[0].id")]),
    R("[admin] Create category", "POST", "/api/categories", role="ADMIN", body={"name":"Pilates"}),
    R("[admin] Update category", "PUT", "/api/categories/{{category_id}}", role="ADMIN",
      body={"id":"{{category_id}}","name":"Yoga & Pilates"}),
    R("[admin] Delete category", "DELETE", "/api/categories/{{category_id}}", role="ADMIN"),
    R("Seller quota", "GET", "/api/sellers/{{user_id}}/quota"),
]))

# ---- 14. Subscription / Me ----
folders.append(F("14 · Plans, Subscriptions & Seller", [
    R("List plans", "GET", "/api/plans", public=True),
    R("My plan", "GET", "/api/me/plan"),
    R("Entitlements", "GET", "/api/me/entitlements"),
    R("Upgrade to PRO", "POST", "/api/me/plan/upgrade", idem=True,
      body={"paymentMethodToken":"tok_visa_xxx","paymentMethodType":"CARD"}),
    R("Cancel plan", "POST", "/api/me/plan/cancel", idem=True),
    R("Checkout bundle", "POST", "/api/bundle-subscriptions/checkout", idem=True,
      body={"bundleId":"{{bundle_id}}","paymentMethodToken":"tok_visa_xxx","paymentMethodType":"CARD",
            "useWalletBalance":False,"promoCode":None}, save=[("bundle_subscription_id","_j.id")]),
    R("My bundle subscriptions", "GET", "/api/me/bundle-subscriptions", q=[("status","ACTIVE")]),
    R("Get bundle subscription", "GET", "/api/bundle-subscriptions/{{bundle_subscription_id}}"),
    R("Cancel bundle subscription", "POST", "/api/me/bundle-subscriptions/{{bundle_subscription_id}}/cancel"),
    R("Generate entry QR", "POST", "/api/me/bundle-subscriptions/{{bundle_subscription_id}}/entry-token/generate"),
    R("Scan entry token (staff)", "POST", "/api/entry-tokens/scan",
      body={"qrPayload":"<qr>","gymId":"{{gym_id}}"}),
    R("My receipts", "GET", "/api/me/receipts"),
    R("Get receipt", "GET", "/api/me/receipts/{{receipt_id}}"),
    R("Get preferences", "GET", "/api/me/preferences"),
    R("Update preferences", "PUT", "/api/me/preferences", body={"hideFromGymLists":True}),
    R("Register payment method", "POST", "/api/me/payment-methods",
      body={"token":"tok_visa_xxx","methodType":"CARD","label":"My Visa","cardBrand":"VISA","last4":"4242"}),
    # seller
    R("[seller] Earnings summary", "GET", "/api/me/earnings/summary"),
    R("[seller] Earnings cohorts", "GET", "/api/me/earnings/cohorts"),
    R("[seller] Payout history", "GET", "/api/me/earnings/payouts"),
    R("[seller] Subscribers", "GET", "/api/me/subscribers", q=[("status","ACTIVE")]),
    R("[seller] Subscriber stats", "GET", "/api/me/subscribers/stats"),
    R("[seller] Revenue", "GET", "/api/me/revenue", q=[("period","30d")]),
    R("[seller] Retention", "GET", "/api/me/retention"),
    R("[seller] Get bank account", "GET", "/api/me/bank-account"),
    R("[seller] Set bank account", "POST", "/api/me/bank-account",
      body={"accountHolderName":"Jane Smith","destination":"1234567890","destinationType":"BANK_ACCOUNT"}),
    R("[seller] Lifecycle", "GET", "/api/me/seller/lifecycle"),
    R("[seller] Start winding-down", "POST", "/api/me/winding-down"),
    R("[seller] Deactivate", "POST", "/api/me/deactivate"),
    R("[gym] Entries", "GET", "/api/me/gym/{{gym_id}}/entries", q=[("period","30d")]),
    R("[gym] Participant notes", "GET", "/api/me/gym/{{gym_id}}/participants/{{target_user_id}}/notes"),
    R("[gym] Add participant note", "POST", "/api/me/gym/{{gym_id}}/participants/{{target_user_id}}/notes",
      body={"note":"Excellent progress"}),
]))

# ---- 15. Payment ----
folders.append(F("15 · Payment", [
    R("Tokenize card", "POST", "/api/payment/payment-methods/tokenize", role="SERVICE (aud=payment)",
      body={"userId":"{{user_id}}","token":"paymob-handle-abc","methodType":"CARD","label":"My Visa","cardBrand":"VISA","last4":"4242"},
      desc="Requires a system JWT (aud=payment); not a normal user token."),
    R("List payment methods", "GET", "/api/payment/payment-methods", role="SERVICE", q=[("userId","{{user_id}}")]),
    R("Delete payment method", "DELETE", "/api/payment/payment-methods/{{payment_method_id}}", role="SERVICE"),
    R("Create charge", "POST", "/api/payment/charges", role="SERVICE", idem=True,
      body={"userId":"{{user_id}}","amountCents":50000,"currency":"EGP","paymentMethodToken":"paymob-handle-abc",
            "description":"Subscription","callerReference":"sub-1"}),
    R("Get charge", "GET", "/api/payment/charges/{{charge_id}}", role="SERVICE"),
    R("Create refund", "POST", "/api/payment/refunds", role="SERVICE", idem=True,
      body={"chargeId":"{{charge_id}}","amountCents":50000,"destination":"WALLET","reason":"customer request"}),
    R("Get refund", "GET", "/api/payment/refunds/{{refund_id}}", role="SERVICE"),
    R("Paymob webhook", "POST", "/api/payment/webhooks/paymob", public=True,
      headers=[{"key":"X-Paymob-Signature","value":"<hmac>"}],
      body={"type":"charge.succeeded","data":{"id":"paymob-ref","amount":50000,"status":"success"}},
      desc="Public but HMAC-verified inside the controller."),
    R("[admin] Reconciliation runs", "GET", "/api/admin/payment/recon", role="ADMIN"),
]))

# ---- 16. Wallet ----
folders.append(F("16 · Wallet", [
    R("My wallet summary", "GET", "/api/wallet/me/summary"),
    R("My wallet entries", "GET", "/api/wallet/me/entries", q=[("page","0"),("size","50")]),
    R("Request withdrawal", "POST", "/api/wallet/me/withdrawals", idem=True,
      body={"amountCents":50000,"destination":{"accountNumber":"1234567890","bankCode":"BNEG"}},
      save=[("withdrawal_id","_j.id")]),
    R("My withdrawals", "GET", "/api/wallet/me/withdrawals"),
    R("Cancel my withdrawal", "POST", "/api/wallet/me/withdrawals/{{withdrawal_id}}/cancel"),
    R("[admin] Get user wallet", "GET", "/api/admin/wallet/{{target_user_id}}", role="ADMIN"),
    R("[admin] List withdrawals", "GET", "/api/admin/wallet/withdrawals", role="ADMIN", q=[("status","SENT"),("userId","")]),
    R("[admin] Cancel withdrawal", "POST", "/api/admin/wallet/withdrawals/{{withdrawal_id}}/cancel", role="ADMIN",
      desc="Force-cancel, incl. SUBMITTING/SENT (asks Payment to abort the payout)."),
    R("[admin] Adjust balance", "POST", "/api/admin/wallet/{{target_user_id}}/adjust", role="ADMIN", idem=True,
      body={"amountCents":25000,"direction":"CREDIT","memo":"correction for duplicate charge"}),
]))

# ---- 17. Schedule ----
folders.append(F("17 · Schedule", [
    R("My schedule", "GET", "/api/schedule/me", q=[("author",""),("date","")]),
    R("Add item", "POST", "/api/schedule/me/items",
      body={"tableType":"EXERCISE","weekday":"MON","content":"Push-ups 3x10","youtubeLink":"dQw4w9WgXcQ"},
      save=[("schedule_item_id","_j.id")]),
    R("Edit item", "PATCH", "/api/schedule/me/items/{{schedule_item_id}}",
      body={"content":"Push-ups 4x12","youtubeLink":None,"clearYoutube":False}),
    R("Delete item", "DELETE", "/api/schedule/me/items/{{schedule_item_id}}"),
    R("Set item completion", "PUT", "/api/schedule/me/items/{{schedule_item_id}}/completion",
      body={"date":"2026-06-18","done":True}),
    R("Set meal target", "PUT", "/api/schedule/me/meal-targets/MON",
      body={"calories":2000,"proteinG":150,"carbsG":250,"fatG":70}),
    R("Set timezone", "PUT", "/api/schedule/me/timezone", body={"timezone":"Africa/Cairo"}),
    R("[trainer] Client schedule", "GET", "/api/schedule/clients/{{target_user_id}}", q=[("date","")],
      desc="Requires an active coaching link."),
    R("[trainer] Add client item", "POST", "/api/schedule/clients/{{target_user_id}}/items",
      body={"tableType":"EXERCISE","weekday":"TUE","content":"Squats 4x8","youtubeLink":None}),
]))

# ---- 18. FitCoach ----
folders.append(F("18 · FitCoach (AI)", [
    R("Chat", "POST", "/api/coach/chat",
      body={"message":"Create a 3-day workout plan for me","profile":{"age":30,"fitness_level":"intermediate"},
            "history":[],"progress_logs":[],"plan_cache":{}},
      desc="The gateway injects the user id; `user_id` in the body is ignored."),
    R("Chat (stream SSE)", "POST", "/api/coach/chat/stream",
      body={"message":"Give me tips for leg day","history":[]},
      desc="Server-Sent Events stream (text/event-stream)."),
    R("Voice (multipart)", "POST", "/api/coach/voice",
      form=[("file","","file"),("profile","{}","text"),("history","[]","text")]),
    R("Analyze media (multipart)", "POST", "/api/coach/analyze",
      form=[("file","","file"),("message","Is my squat form correct?","text"),("is_first_message","false","text")]),
    R("Feedback", "POST", "/api/coach/feedback",
      body={"entry_id":"<memory-entry-id>","feedback":1,"note":"Great advice!"}),
    R("Health", "GET", "/api/coach/health", public=True),
]))

# ---- 19. Admin (cross-cutting) ----
folders.append(F("19 · Admin (Platform & Sellers)", [
    R("[admin] Role requests", "GET", "/api/admin/role-requests", role="ADMIN", q=[("status",""),("page","0"),("size","20")]),
    R("[admin] Approve role request", "POST", "/api/admin/role-requests/{{role_request_id}}/approve", role="ADMIN"),
    R("[admin] Request changes", "POST", "/api/admin/role-requests/{{role_request_id}}/request-changes", role="ADMIN",
      body={"message":"Provide a recent certificate"}),
    R("[admin] Reject role request", "POST", "/api/admin/role-requests/{{role_request_id}}/reject", role="ADMIN",
      body={"message":"Certificate invalid"}),
    R("[admin] Platform subscriptions", "GET", "/api/admin/platform/subscriptions", role="ADMIN"),
    R("[admin] Platform escrow", "GET", "/api/admin/platform/escrow", role="ADMIN"),
    R("[admin] Platform refunds", "GET", "/api/admin/platform/refunds", role="ADMIN"),
    R("[admin] Platform dunning", "GET", "/api/admin/platform/dunning", role="ADMIN"),
    R("[admin] Platform revenue", "GET", "/api/admin/platform/revenue", role="ADMIN"),
    R("[admin] Top sellers", "GET", "/api/admin/sellers/top", role="ADMIN"),
    R("[admin] Freeze seller", "POST", "/api/admin/sellers/{{target_user_id}}/freeze", role="ADMIN", body={"reason":"fraud suspected"}),
    R("[admin] Unfreeze seller", "POST", "/api/admin/sellers/{{target_user_id}}/unfreeze", role="ADMIN", body={"reason":"cleared"}),
    R("[admin] Ban seller", "POST", "/api/admin/sellers/{{target_user_id}}/ban", role="ADMIN", body={"reason":"ToS violations"}),
    R("[admin] Wind down seller", "POST", "/api/admin/sellers/{{target_user_id}}/winding-down", role="ADMIN", body={"reason":"requested"}),
    R("[admin] Revert winding-down", "POST", "/api/admin/sellers/{{target_user_id}}/winding-down/revert", role="ADMIN", body={"reason":"changed mind"}),
]))

# ======================================================================
# WORKFLOWS (chained, ordered; run the folder top-to-bottom)
# ======================================================================
def wf_login():
    return R("1. Login (admin)", "POST", "/api/auth/login", public=True,
        body={"email":"admin@dumble.local","password":"{{admin_password}}"},
        tests=["var j = pm.response.json();",
               "pm.test('login ok', () => pm.response.to.have.status(200));",
               "pm.collectionVariables.set('auth_token', j.accessToken);",
               "pm.collectionVariables.set('refresh_token', j.refreshToken);",
               "pm.collectionVariables.set('user_id', j.user.id);"])

workflows = F("00 · WORKFLOWS (run in order)", [
    F("A · Auth & Profile", [
        R("1. Register a new user", "POST", "/api/auth/register", public=True,
          body={"firstName":"Grace","lastName":"Hopper","email":"grace+{{$timestamp}}@dumble.test","password":"Passw0rd!23"},
          tests=["var j=pm.response.json();",
                 "pm.test('registered', ()=>pm.expect(pm.response.code).to.be.oneOf([200,201]));",
                 "pm.collectionVariables.set('auth_token', j.accessToken);",
                 "pm.collectionVariables.set('user_id', j.user.id);"]),
        R("2. Get my profile", "GET", "/api/users/me",
          tests=["pm.test('has id', ()=>pm.expect(pm.response.json().id).to.eql(pm.collectionVariables.get('user_id')));"]),
        R("3. Complete onboarding", "PATCH", "/api/users/me/onboarding",
          body={"dateOfBirth":"1990-01-01","gender":"FEMALE","weight":62,"height":170,"fitnessGoals":["endurance"]}),
        R("4. Update profile", "PATCH", "/api/users/me", body={"displayName":"Grace H","bio":"Compiler pioneer"}),
    ]),
    F("B · Post lifecycle", [
        wf_login(),
        R("2. Create a post", "POST", "/api/posts",
          form=[("Content","E2E workflow post #test","text"),("Hashtags","test","text")],
          tests=["var j=pm.response.json();",
                 "pm.test('created', ()=>pm.expect(pm.response.code).to.be.oneOf([200,201]));",
                 "pm.collectionVariables.set('post_id', j.id);"]),
        R("3. Read the post", "GET", "/api/posts/{{post_id}}", public=True,
          tests=["pm.test('matches id', ()=>pm.expect(pm.response.json().id).to.eql(pm.collectionVariables.get('post_id')));"]),
        R("4. React to it", "POST", "/api/posts/{{post_id}}/reactions", body={"type":"like"}),
        R("5. Comment on it", "POST", "/api/posts/{{post_id}}/comments", body={"content":"workflow comment"},
          tests=["var j=pm.response.json(); pm.collectionVariables.set('comment_id', j.id);",
                 "pm.test('comment created', ()=>pm.expect(pm.response.code).to.be.oneOf([200,201]));"]),
        R("6. List comments", "GET", "/api/posts/{{post_id}}/comments", public=True),
        R("7. Update the post", "PUT", "/api/posts/{{post_id}}", body={"content":"edited workflow post","hashtags":["test"]}),
        R("8. Delete the post", "DELETE", "/api/posts/{{post_id}}"),
    ]),
    F("C · Social graph & feed", [
        wf_login(),
        R("2. Follow a user", "POST", "/api/social/follow/{{target_user_id}}",
          desc="Set {{target_user_id}} first."),
        R("3. Track a view", "POST", "/api/feed/behavior", body={"postId":"{{post_id}}","eventType":"View","eventData":None}),
        R("4. Home feed", "GET", "/api/feed/home", q=[("limit","20")]),
        R("5. Suggested users", "GET", "/api/feed/suggested-users", q=[("limit","10")]),
        R("6. Unfollow", "DELETE", "/api/social/follow/{{target_user_id}}"),
    ]),
    F("D · Chat", [
        wf_login(),
        R("2. Create a direct conversation", "POST", "/api/chat/conversations",
          body={"type":"Direct","name":None,"participantIds":["{{target_user_id}}"]},
          tests=["var j=pm.response.json(); pm.collectionVariables.set('conversation_id', j.id);",
                 "pm.test('created', ()=>pm.expect(pm.response.code).to.be.oneOf([200,201]));"]),
        R("3. Send a message", "POST", "/api/chat/conversations/{{conversation_id}}/messages",
          body={"content":"Hello from the workflow","replyToMessageId":None,"imageUrl":None},
          tests=["var j=pm.response.json(); pm.collectionVariables.set('message_id', j.id);",
                 "pm.test('sent', ()=>pm.expect(pm.response.code).to.be.oneOf([200,201]));"]),
        R("4. List messages", "GET", "/api/chat/conversations/{{conversation_id}}/messages"),
        R("5. Mark read", "PUT", "/api/chat/conversations/{{conversation_id}}/read", q=[("messageId","{{message_id}}")]),
    ]),
], desc="Ordered end-to-end scenarios. Set `base_url` + `admin_password` (and `target_user_id` where noted), then Run each sub-folder top-to-bottom. Scripts capture ids/tokens into collection variables automatically.")

# ======================================================================
# COLLECTION
# ======================================================================
variables = [
    ("base_url","{{base_url}}"),("admin_password",""),("auth_token",""),("refresh_token",""),
    ("user_id",""),("target_user_id",""),("post_id",""),("comment_id",""),("conversation_id",""),
    ("message_id",""),("notification_id",""),("gym_id",""),("registration_id",""),("bundle_id",""),
    ("category_id",""),("bundle_subscription_id",""),("receipt_id",""),("withdrawal_id",""),
    ("charge_id",""),("refund_id",""),("payment_method_id",""),("schedule_item_id",""),("role_request_id",""),
]
collection = {
    "info": {
        "name": "Dumble Backend API",
        "_postman_id": "dumble-backend-collection-2026",
        "description": ("Complete API for the Dumble fitness platform — all services behind the gateway.\n\n"
            "**Setup:** import an environment (Local or Production), set `admin_password`, run **00 · WORKFLOWS → B · Post lifecycle → 1. Login** "
            "(or any login) to populate `auth_token`. Collection-level Bearer auth then applies it to every request.\n\n"
            "Folders are grouped by resource. The `00 · WORKFLOWS` folder has ordered end-to-end scenarios."),
        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
    },
    "auth": {"type": "bearer", "bearer": [{"key": "token", "value": "{{auth_token}}", "type": "string"}]},
    "event": [
        {"listen": "prerequest", "script": {"type": "text/javascript", "exec": [""]}},
        {"listen": "test", "script": {"type": "text/javascript", "exec": [""]}},
    ],
    "variable": [{"key": k, "value": v} for k, v in variables],
    "item": [workflows] + folders,
}

with open(os.path.join(OUT, "Dumble-Backend.postman_collection.json"), "w", encoding="utf-8") as f:
    json.dump(collection, f, indent=2)

# Environments
def env(name, base):
    return {"id": "dumble-"+name.lower(), "name": name,
            "values": [
                {"key":"base_url","value":base,"enabled":True},
                {"key":"admin_password","value":"","enabled":True},
            ],
            "_postman_variable_scope":"environment"}

with open(os.path.join(OUT, "Dumble-Local.postman_environment.json"), "w", encoding="utf-8") as f:
    json.dump(env("Dumble Local","http://localhost:8080"), f, indent=2)
with open(os.path.join(OUT, "Dumble-Production.postman_environment.json"), "w", encoding="utf-8") as f:
    json.dump(env("Dumble Production","http://167.172.180.189:8080"), f, indent=2)

# count endpoints
def count(items):
    n = 0
    for it in items:
        if "item" in it: n += count(it["item"])
        elif "request" in it: n += 1
    return n
print("Endpoints (incl. workflows):", count(collection["item"]))
print("Top-level folders:", len(collection["item"]))
print("Wrote files to", OUT)
