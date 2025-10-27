package org.etwas.streamtweaks.twitch.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TwitchOAuthClientTest {

    @InjectMocks
    private TwitchOAuthClient sut;

    @Mock
    private TwitchCredentialStore credentialStore;

    @Test
    void testHasValidTokenWithNoCredentialStore() {
        TwitchCredentials emptyCredentials = new TwitchCredentials(null, null);
        when(credentialStore.loadOrCreate()).thenReturn(emptyCredentials);

        boolean actual = sut.hasValidToken().join();

        assertFalse(actual, "Should return false when there is no access token.");
        verify(credentialStore, times(1)).loadOrCreate();
        verifyNoMoreInteractions(credentialStore);
    }
}
