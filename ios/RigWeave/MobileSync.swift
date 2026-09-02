import CryptoKit
import Foundation
import Security
import Sodium

struct AppleMobileSyncSpace:Identifiable{let id:String;let stationId:String;let logbookId:String;let mode:String;let authority:String;let keyVersion:Int;let state:String}
struct AppleMobileSyncDashboard{let spaces:[AppleMobileSyncSpace];let pending:Int;let devices:Int;let conflicts:Int;let domains:Int;static let empty=AppleMobileSyncDashboard(spaces:[],pending:0,devices:0,conflicts:0,domains:0)}
struct AppleMobileCiphertext{let ciphertext:[UInt8];let nonce:[UInt8]}

/** Audited Swift-Sodium adapter shared with the Node and Android protocol choices. */
final class AppleMobileSyncCrypto{
    private let sodium=Sodium()
    func newSpaceKey()->[UInt8]{sodium.aead.xchacha20poly1305ietf.key()}
    func newDeviceBoxKeyPair()->Box.KeyPair?{sodium.box.keyPair()}
    func encrypt(_ plaintext:Data,associatedData:Data,key:[UInt8])->AppleMobileCiphertext?{
        guard let sealed:(authenticatedCipherText:[UInt8],nonce:[UInt8])=sodium.aead.xchacha20poly1305ietf.encrypt(message:[UInt8](plaintext),secretKey:key,additionalData:[UInt8](associatedData)) else{return nil}
        return AppleMobileCiphertext(ciphertext:sealed.authenticatedCipherText,nonce:sealed.nonce)
    }
    func decrypt(_ value:AppleMobileCiphertext,associatedData:Data,key:[UInt8])->Data?{
        let combined=value.nonce+value.ciphertext
        return sodium.aead.xchacha20poly1305ietf.decrypt(nonceAndAuthenticatedCipherText:combined,secretKey:key,additionalData:[UInt8](associatedData)).map{Data($0)}
    }
    func sealSpaceKey(_ key:[UInt8],recipientPublicKey:[UInt8])->[UInt8]?{sodium.box.seal(message:key,recipientPublicKey:recipientPublicKey)}
    func openSpaceKey(_ envelope:[UInt8],recipient:Box.KeyPair)->[UInt8]?{sodium.box.open(anonymousCipherText:envelope,recipientPublicKey:recipient.publicKey,recipientSecretKey:recipient.secretKey)}
}

/** Stable P-256 request identity. The private SecKey is permanent and never exported. */
final class AppleMobileDeviceIdentity{
    private let tag=Data("app.rigweave.mobile.m9.identity.v1".utf8)
    private lazy var privateKey:SecKey?={
        let query:[String:Any]=[kSecClass as String:kSecClassKey,kSecAttrApplicationTag as String:tag,kSecAttrKeyType as String:kSecAttrKeyTypeECSECPrimeRandom,kSecReturnRef as String:true]
        var found:CFTypeRef?;if SecItemCopyMatching(query as CFDictionary,&found)==errSecSuccess{return (found as! SecKey)}
        var attributes:[String:Any]=[kSecAttrKeyType as String:kSecAttrKeyTypeECSECPrimeRandom,kSecAttrKeySizeInBits as String:256,kSecPrivateKeyAttrs as String:[kSecAttrIsPermanent as String:true,kSecAttrApplicationTag as String:tag,kSecAttrAccessible as String:kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly]]
        #if !targetEnvironment(simulator)
        attributes[kSecAttrTokenID as String]=kSecAttrTokenIDSecureEnclave
        #endif
        var error:Unmanaged<CFError>?;return SecKeyCreateRandomKey(attributes as CFDictionary,&error)
    }()
    var fingerprintSha256:String{guard let data=publicKeyData else{return "KEYCHAIN_UNAVAILABLE"};return SHA256.hash(data:data).map{String(format:"%02x",$0)}.joined()}
    var deviceId:String{"rw-"+fingerprintSha256.prefix(32)}
    var publicKeyPem:String?{guard let raw=publicKeyData else{return nil};let prefix=Data([0x30,0x59,0x30,0x13,0x06,0x07,0x2a,0x86,0x48,0xce,0x3d,0x02,0x01,0x06,0x08,0x2a,0x86,0x48,0xce,0x3d,0x03,0x01,0x07,0x03,0x42,0x00]);let b64=(prefix+raw).base64EncodedString();return "-----BEGIN PUBLIC KEY-----\n"+stride(from:0,to:b64.count,by:64).map{index in let start=b64.index(b64.startIndex,offsetBy:index);let end=b64.index(start,offsetBy:min(64,b64.distance(from:start,to:b64.endIndex)));return String(b64[start..<end])}.joined(separator:"\n")+"\n-----END PUBLIC KEY-----"}
    func sign(_ message:Data)->Data?{guard let privateKey else{return nil};var error:Unmanaged<CFError>?;return SecKeyCreateSignature(privateKey,.ecdsaSignatureMessageX962SHA256,message as CFData,&error) as Data?}
    private var publicKeyData:Data?{guard let privateKey,let publicKey=SecKeyCopyPublicKey(privateKey) else{return nil};var error:Unmanaged<CFError>?;return SecKeyCopyExternalRepresentation(publicKey,&error) as Data?}
}

enum AppleMobileSyncSecretStore{
    static func put(account:String,data:Data)->Bool{remove(account:account);let query:[String:Any]=[kSecClass as String:kSecClassGenericPassword,kSecAttrService as String:"app.rigweave.mobile.m9.sync",kSecAttrAccount as String:account,kSecAttrAccessible as String:kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,kSecValueData as String:data];return SecItemAdd(query as CFDictionary,nil)==errSecSuccess}
    static func get(account:String)->Data?{let query:[String:Any]=[kSecClass as String:kSecClassGenericPassword,kSecAttrService as String:"app.rigweave.mobile.m9.sync",kSecAttrAccount as String:account,kSecReturnData as String:true];var value:CFTypeRef?;return SecItemCopyMatching(query as CFDictionary,&value)==errSecSuccess ? value as? Data:nil}
    static func remove(account:String){SecItemDelete([kSecClass as String:kSecClassGenericPassword,kSecAttrService as String:"app.rigweave.mobile.m9.sync",kSecAttrAccount as String:account] as CFDictionary)}
}
