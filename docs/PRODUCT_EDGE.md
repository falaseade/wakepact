# WakePact — Product Edge Report

*Generated via the `product-edge` skill on 2026-06-16. Competitive facts come from
targeted web searches (June 2026) plus `MARKET.md`. The automated deep-research pass
hit a hard rate limit and returned empty; the figures below are from manual verification.
Pricing and review themes are sourced and dated; unverified items are flagged. The
Sleep as Android "$84.99 lifetime" figure looked like an aggregator artifact and is
flagged as uncertain.*

## 1. The verdict — Do this next

**The one move:** **Ship buddy push notifications (FCM + the Cloud Function on `ringEvents`) this sprint.**
Today the differentiator — "a friend holds the off-switch" — only works if the buddy already has
the app *open* at 6 a.m. The buddy is asleep too. Until push lands, the pact is decorative and the
positioning collapses. This is roadmap item #1 for a reason; it's the critical path.

**Why it's first:** It's the one thing between "clever demo" and "a product whose core loop works in
the real world." Pricing, the store listing, and mission variety are all downstream of a pact that
functions. Low-to-medium effort (a small function + FCM wiring already sketched in `FIREBASE_SETUP.md`),
highest possible impact.

**The next 5 moves, in order:**
1. **Ship buddy push (FCM).** Make the off-switch reachable when the buddy's phone is locked.
2. **Lock the pricing model: pact stays free, sell a one-time "Pro" unlock** for mission variety +
   stats + widgets (§5). Wire the paywall around *capability*, never around the social loop.
3. **Build the Play listing and run an ASO pass** that counter-positions against Alarmy's top
   complaints: *no ads, can't grade your own homework, one fair price.*
4. **Ship the QR/barcode mission** (roadmap #2) — strongest anti-spoof and the flagship Pro feature.
5. **Add streaks + per-member stats** on the Pact screen — retention and social pressure, near-zero
   effort since the data is already in the feed.

**The bet:** *Be the anti-Alarmy.* Keep the social pact free forever to win the network effect and
trust; monetize solo power-users and mission variety with a one-time price and zero ads. Win the
wedge of **friend-groups, flatmates and couples with one chronic snoozer**, where the invite-a-friend
requirement *is* the growth engine.

## 2. Executive summary

WakePact sits in a genuinely open seam. Every incumbent owns *half* the problem: Alarmy verifies
wakefulness but lets you grade your own homework (and is drowning in paywall/ads complaints); Galarm
adds people but verifies nothing and only *notifies* buddies; Wakie's stranger-call model has been
abandoned (the company pivoted to a voice social network). **No funded, active app fuses sensor-proof
+ a friend-held off-switch.** That's the wedge. The risk is not competition — it's execution: the
core loop doesn't work until push notifications ship.

## 3. Competitive landscape

| Capability / dimension | **WakePact** | Alarmy | Galarm | Wakie | Sleep as Android |
|---|---|---|---|---|---|
| Wake-proof mission (must *do* something) | ✅ walk N steps, anti-spoof cadence | ✅ steps/photo/math/squat/typing/QR | ❌ | ❌ | ◐ CAPTCHA/QR/math |
| **Human accountability** | ✅ **friend holds off-switch** | ❌ solo | ◐ buddies *notified* only | ◐ stranger *calls* you | ❌ solo |
| Proof someone is actually up | ✅ sensor proof **+** human confirm | ◐ self-verified | ❌ | ❌ | ◐ self-verified |
| Remote off-switch in a friend's hand | ✅ | ❌ | ❌ | ❌ | ❌ |
| Works fully offline / solo | ✅ first-class | ✅ | ◐ needs account | ❌ | ✅ |
| Reliability (fires, survives reboot) | ✅ foreground service, reboot-safe | ⚠️ frequent "didn't wake me" complaints | ✅ | n/a | ✅ strong |
| Ads | 🚫 **none, ever** (design principle) | ⚠️ heavy, *at wake time* | minimal | — | none |
| Pricing | **TBD — see §5** | Free + Premium ~$5/mo / **$59.99/yr** (£7.49/mo intl) | Free + Premium **$0.99/mo / $9.99/yr / $24.99 lifetime** | free (pivoted) | Free trial → one-time unlock or $4.99/mo / $49.99/yr cloud |
| What's gated | n/a yet | 4 of 8 missions free; **rest paywalled** | unlimited group/buddy alarms, web, ringtones | — | cloud, add-ons |
| Positioning one-liner | "The alarm a friend holds the off-switch to" | "The alarm that makes you get up" | "Alarms & reminders with your people" | "Voice social network" | "Smart alarm + sleep tracking" |

**Per-competitor read:**
- **Alarmy** — the 800-lb gorilla (~$11M+ revenue, category-defining "missions" UX). *Exploitable
  weakness:* reviews are a wall of resentment — *"missions that used to be free are now charged,"
  "dozens of ads when waking up," "didn't wake me 3 mornings, waste of $10," "feels like a scam,"*
  battery drain. And it's solo — you verify yourself. The brand can be "Alarmy without the betrayal."
- **Galarm** — mature, beloved, proves **social alarms can be free and still huge**; group/buddy
  alarms are its hook. *Weakness:* buddies are notified, never *empowered* — no wake-proof, no remote
  off-switch. Validates the social DNA without competing on the mechanism.
- **Wakie** — raised ~$4M, then **pivoted away from alarms** to a voice social network. Stranger-call
  didn't hold — strangers = zero lasting accountability. *Lesson:* friends, not strangers, is the
  durable thesis; the niche is vacant.
- **Sleep as Android** — deep reliability know-how, freemium with a one-time unlock or cloud sub.
  Alarms are one feature in a big sleep suite — solo verification again, not the same fight.

## 4. Gaps & ideas — ranked by why they win

**Quick wins (high impact / low effort) — do first**
- **Buddy push notifications (FCM).** *Pain:* the buddy is asleep with the app closed, so the
  off-switch is unreachable. *Why it wins:* without it the differentiator doesn't function.
  **Effort: S–M. Move #1.**
- **Streaks + per-member stats.** *Pain:* habit needs a visible scoreboard. *Why it wins:* retention
  + amplifies the social pressure that *is* the product; data already in the feed. **Effort: S.**
- **Counter-positioned store listing (ASO).** *Pain:* nobody knows you exist. *Why it wins:* harvests
  Alarmy's disillusioned reviewers directly. **Effort: S.**

**Big bets (high impact / higher effort)**
- **QR/barcode "walk-to-it" mission.** *Pain:* "I can fake steps by swinging my arm." *Why it wins:*
  strongest anti-spoof and a flagship paid feature that leapfrogs Alarmy's QR by combining it with the
  buddy gate. **Effort: M.**
- **Multiple pacts + per-alarm pact selection.** *Pain:* my gym buddy ≠ my flatmate. *Why it wins:*
  more invite surfaces = more viral loops, and a natural Pro gate. **Effort: M.**

**Fast-follows / parity**
- Plain-dismiss preference for solo users who want a normal alarm (cuts "I just want an alarm" churn). **S.**
- Home-screen widget (next alarm + pact pending count) — Pro sweetener. **S–M.**
- Alarm queueing for overlaps (today a second alarm mid-ring is skipped). **M.**

**Skip / defer**
- **Wager mode / money-on-the-line** — heavy payments compliance; defer past PMF.
- **iOS / Wear OS** — only after the Android loop is proven and viral.
- **Stranger-based anything** — Wakie proved it doesn't retain.

## 5. Monetization & positioning

**Model: freemium, one-time unlock primary. Keep the pact FREE.**

The crux: **never gate the social loop.** The pact *requires* inviting a friend — that invite is the
growth engine (built-in K-factor). Paywalling it throttles the one thing that makes WakePact spread.
Galarm proves free social scales; Alarmy proves missions monetize. Take the free-social half from
Galarm and the paid-mission half from Alarmy.

| Tier | Price | What's in it |
|---|---|---|
| **Free forever** | £0 | Unlimited basic alarms, **step mission, one pact, solo mode, the feed.** No ads, ever. |
| **WakePact Pro** | **One-time £6.99 / $7.99** (or £1.49/mo for the subscription-minded) | QR/photo/math missions, **streaks & stats**, widgets, **multiple pacts**, custom sound packs. |
| **Pact Pro (group unlock)** | one member pays → **whole pact unlocked** | Fits the social DNA; the payer evangelizes the invite. The cleverest revenue lever. |

*Why one-time, not subscription:* the users are students/shift-workers — price-sensitive and
subscription-fatigued — and Alarmy's loudest complaint is its aggressive subscription. A fair one-time
price *is* the positioning. Don't gate alarm *count* (petty, off-brand); gate *capability*.

**Positioning line:** *"The alarm you can't talk yourself out of — a friend holds the off-switch, and
no amount of snoozing reaches it."* Store sub-line: *"No ads. No grading your own homework. One fair
price."* — every clause jabs at an Alarmy review complaint.

**GTM wedge:** Beachhead = **friend-pairs and flatmates with one chronic snoozer** (gym buddies,
couples, uni flats, night-shift teams). Channel = Reddit (r/GetDisciplined, r/college, r/nightshift,
r/getup) and short-form "accountability/5am" TikTok, where the hook demos itself in 10 seconds. The
acquisition math favors you: **you can't onboard one user without them recruiting a second** — engineer
that invite to be frictionless and the product is its own growth loop.

## 6. Action plan

**Now (this sprint)**
1. **Buddy push (FCM + `ringEvents` function).** *Why:* core loop is non-functional without it.
   *Success signal:* a buddy's locked phone chimes within ~5s of a proof-done event and can deactivate
   from the notification.
2. **Lock pricing & build the paywall** around missions/stats/widgets — *not* the pact. *Success
   signal:* the pact and step mission remain free in every build; paywall only appears on advanced
   missions/stats.

**Next (this month)**
3. **Play listing + ASO pass**, counter-positioned vs Alarmy. *Success signal:* listing copy explicitly
   answers the top-3 Alarmy complaints; first store-conversion baseline captured.
4. **QR/barcode mission.** *Success signal:* set-up-and-rescan flow works; it's the headline Pro feature.

**Later**
5. **Streaks/stats, multiple pacts, widget, plain-dismiss.** *Success signal:* D7 retention and
   pact-invite rate (invites sent per new user) trending up — invite rate is the north-star, since it's
   both growth and engagement.

*Mapped to the goal — find a wedge + a monetization model to launch with:* moves 1–2 make the wedge
real and bankable; 3–4 acquire into it; 5 compounds it.

## Sources

- Alarmy pricing — [Adapty paywall library](https://adapty.io/paywall-library/alarmy/),
  [Alarmy Android Premium guide](https://alarmy-android.zendesk.com/hc/en-us/articles/900000291503--Management-What-is-Alarmy-Pro-or-subscription)
- Alarmy complaints — [justuseapp reviews](https://justuseapp.com/en/app/1163786766/alarmy-morning-alarm-clock/reviews),
  [nomoresnooze review](https://www.nomoresnooze.co/p/alarmy-app-review)
- Galarm pricing/features — [galarmapp.com/premium](https://www.galarmapp.com/premium)
- Wakie status/pivot — [Tracxn profile](https://tracxn.com/d/companies/wakie/__jjl_m94f1Zk4iZBokd37p42IhSHIEFdYGVKojYLCKSM),
  [Wikipedia](https://en.wikipedia.org/wiki/Wakie)
- Sleep as Android pricing — [urbandroid pricing docs](https://sleep.urbandroid.org/docs/general/plan.html)
  *(lifetime figure uncertain — aggregator artifact)*
- Prior internal scan — `docs/MARKET.md` (2026-06-12)
