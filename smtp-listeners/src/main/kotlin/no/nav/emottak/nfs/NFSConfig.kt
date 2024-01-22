package no.nav.emottak.nfs

import no.nav.emottak.smtp.getEnvVar

class NFSConfig {

    val nfsKey = getEnvVar("CPA_NFS_PRIVATEKEY", "notavailable")
}
