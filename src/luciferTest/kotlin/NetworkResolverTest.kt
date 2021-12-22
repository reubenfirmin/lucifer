import service.NetworkProcessResolver
import service.WhoisResolver
import kotlin.test.Test

class NetworkResolverTest {

    @Test
    fun `can run resolver and parse results`() {
        NetworkProcessResolver(ByteArray(4096), WhoisResolver(ByteArray(4096)))
        // TODO examine results
    }
}