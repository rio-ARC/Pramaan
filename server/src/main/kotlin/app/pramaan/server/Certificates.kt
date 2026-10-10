package app.pramaan.server

import app.pramaan.core.manifest.SessionManifest
import app.pramaan.core.model.Badge
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.io.File
import java.util.Date

class ServerCertificateIssuer(
    keyFile: File = File("server_key.json")
) {
    private val ecKey: ECKey = if (keyFile.exists()) {
        ECKey.parse(keyFile.readText())
    } else {
        val generated = ECKeyGenerator(Curve.P_256)
            .keyID("pramaan-auth-1")
            .generate()
        keyFile.writeText(generated.toJSONString())
        generated
    }

    private val signer = ECDSASigner(ecKey.toECPrivateKey())
    val verifier = ECDSAVerifier(ecKey.toECPublicKey())

    /**
     * Issues an ES256 compact JWS talent credential for a verified session (PRD 10.6).
     */
    fun issueCertificate(
        manifest: SessionManifest,
        finalBadge: Badge,
        witnessShortId: String? = null
    ): String {
        val now = Date()
        val claimsBuilder = JWTClaimsSet.Builder()
            .issuer("pramaan.authority")
            .subject(manifest.athlete.id)
            .claim("sid", manifest.sessionId)
            .claim("ini", manifest.athlete.initials)
            .claim("tst", manifest.test.name)
            .claim("val", manifest.result.best)
            .claim("unt", manifest.result.unit)
            .claim("bdg", finalBadge.name)
            .claim("cpk", manifest.devicePublicKey.take(16))
            .issueTime(now)

        if (witnessShortId != null) {
            claimsBuilder.claim("wit", witnessShortId)
        }

        val claims = claimsBuilder.build()
        val header = JWSHeader.Builder(JWSAlgorithm.ES256)
            .keyID(ecKey.keyID)
            .build()

        val signedJWT = SignedJWT(header, claims)
        signedJWT.sign(signer)
        return signedJWT.serialize()
    }

    fun verifyCertificate(jws: String): JWTClaimsSet? {
        return try {
            val signedJWT = SignedJWT.parse(jws)
            if (signedJWT.verify(verifier)) {
                signedJWT.jwtClaimsSet
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
