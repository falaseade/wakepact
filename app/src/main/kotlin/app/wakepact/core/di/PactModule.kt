package app.wakepact.core.di

import app.wakepact.data.alarm.AlarmRepository
import app.wakepact.data.identity.IdentityRepository
import app.wakepact.data.pact.FcmPactMessaging
import app.wakepact.data.pact.FirebaseProvider
import app.wakepact.data.pact.FirestorePactGateway
import app.wakepact.data.pact.LocalPactGateway
import app.wakepact.data.pact.NoOpPactMessaging
import app.wakepact.data.pact.PactGateway
import app.wakepact.data.pact.PactMessaging
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PactModule {

    /** Binds the live Firestore gateway when Firebase is configured, solo gateway otherwise. */
    @Provides
    @Singleton
    fun providePactGateway(
        firebaseProvider: FirebaseProvider,
        identityRepository: IdentityRepository,
        alarmRepository: AlarmRepository,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): PactGateway {
        val app = firebaseProvider.app
        return if (app != null) {
            FirestorePactGateway(app, identityRepository, ioDispatcher)
        } else {
            LocalPactGateway(identityRepository, alarmRepository)
        }
    }

    /** FCM topic membership when Firebase is configured; a no-op in solo mode. */
    @Provides
    @Singleton
    fun providePactMessaging(firebaseProvider: FirebaseProvider): PactMessaging =
        if (firebaseProvider.app != null) FcmPactMessaging() else NoOpPactMessaging
}
