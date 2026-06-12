package app.wakepact.domain.model

data class PactMember(val uid: String, val name: String)

data class Pact(
    val id: String,
    val name: String,
    val inviteCode: String,
    val members: List<PactMember>,
)
