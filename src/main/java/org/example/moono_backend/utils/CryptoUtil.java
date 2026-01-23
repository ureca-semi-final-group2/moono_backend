package org.example.moono_backend.utils;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.*;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePBEDataDecryptorFactoryBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.Security;

/**
 * PostgreSQL pgcrypto의 pgp_sym_encrypt/decrypt 호환 복호화 유틸리티
 * 
 * 역할:
 * - PostgreSQL에서 pgp_sym_encrypt로 암호화된 데이터를 Java에서 복호화
 * - BouncyCastle 라이브러리를 사용하여 OpenPGP 표준 복호화 지원
 * 
 * 사용 시나리오:
 * 1. DB에 암호화된 상태로 저장된 개인정보 (이름, 이메일, 전화번호)
 * 2. Kafka를 통해 암호화된 상태로 전송된 데이터
 * 3. Consumer 단계에서 실제 이메일/SMS 발송 시 복호화 필요
 * 
 * 보안 정책:
 * - 이메일 발송 시: 복호화하여 실제 값으로 발송
 * - SMS 발송 시: 복호화하여 실제 전화번호로 발송
 * - 로그 저장 시: 암호화된 상태 유지 (복호화 하지 않음)
 * 
 * PostgreSQL 쿼리 예시:
 * <pre>
 * -- 암호화
 * INSERT INTO member_credential (name, email, phone_number)
 * VALUES (
 *   pgp_sym_encrypt('홍길동', 'secret-key'),
 *   pgp_sym_encrypt('user@example.com', 'secret-key'),
 *   pgp_sym_encrypt('010-1234-5678', 'secret-key')
 * );
 * 
 * -- 복호화 (PostgreSQL)
 * SELECT 
 *   pgp_sym_decrypt(name::bytea, 'secret-key') as name,
 *   pgp_sym_decrypt(email::bytea, 'secret-key') as email
 * FROM member_credential;
 * </pre>
 */
@Slf4j
@Component
public class CryptoUtil {

    @Value("${crypto.secret-key}")
    private String secretKey;

    @Value("${crypto.enabled:true}")
    private boolean enabled;

    static {
        // BouncyCastle Provider 등록 (OpenPGP 지원)
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * PostgreSQL pgp_sym_decrypt 호환 복호화
     * 
     * @param encryptedData 암호화된 데이터 (PostgreSQL bytea에서 가져온 문자열)
     * @return 복호화된 평문
     * @throws RuntimeException 복호화 실패 시
     */
    public String decrypt(String encryptedData) {
        // 복호화 비활성화 시 원본 반환 (테스트 환경)
        if (!enabled) {
            log.debug("[CryptoUtil] 복호화 비활성화 - 원본 반환");
            return encryptedData;
        }

        // null 또는 빈 문자열은 그대로 반환
        if (encryptedData == null || encryptedData.isEmpty()) {
            return encryptedData;
        }

        try {
            log.debug("[CryptoUtil] 복호화 시작. 데이터 길이: {}", encryptedData.length());

            // PostgreSQL bytea 형식 파싱
            byte[] encryptedBytes = parsePostgresByteA(encryptedData);
            
            // PGP 메시지 스트림 생성
            InputStream inputStream = new ByteArrayInputStream(encryptedBytes);
            InputStream decoderStream = PGPUtil.getDecoderStream(inputStream);
            
            // PGP 객체 팩토리 생성
            PGPObjectFactory pgpFactory = new PGPObjectFactory(
                decoderStream,
                new BcKeyFingerprintCalculator()
            );

            // 암호화된 데이터 리스트 추출
            Object obj = pgpFactory.nextObject();
            PGPEncryptedDataList encryptedDataList;
            
            if (obj instanceof PGPEncryptedDataList) {
                encryptedDataList = (PGPEncryptedDataList) obj;
            } else {
                // 첫 번째 객체가 마커인 경우 다음 객체 읽기
                encryptedDataList = (PGPEncryptedDataList) pgpFactory.nextObject();
            }

            // PGP 대칭키 암호화 데이터 추출
            PGPPBEEncryptedData pbeEncryptedData = (PGPPBEEncryptedData) encryptedDataList.get(0);
            
            // 복호화 팩토리 생성 및 데이터 스트림 열기
            InputStream decryptedStream = pbeEncryptedData.getDataStream(
                new JcePBEDataDecryptorFactoryBuilder(
                    new JcaPGPDigestCalculatorProviderBuilder().build()
                ).setProvider(BouncyCastleProvider.PROVIDER_NAME).build(secretKey.toCharArray())
            );

            // 복호화된 데이터에서 리터럴 데이터 추출
            PGPObjectFactory plainFactory = new PGPObjectFactory(
                decryptedStream,
                new BcKeyFingerprintCalculator()
            );

            Object plainObj = plainFactory.nextObject();
            
            // 압축된 데이터인 경우 압축 해제
            if (plainObj instanceof PGPCompressedData) {
                PGPCompressedData compressedData = (PGPCompressedData) plainObj;
                InputStream compressedStream = compressedData.getDataStream();
                plainFactory = new PGPObjectFactory(
                    compressedStream,
                    new BcKeyFingerprintCalculator()
                );
                plainObj = plainFactory.nextObject();
            }

            // 리터럴 데이터 추출
            if (plainObj instanceof PGPLiteralData) {
                PGPLiteralData literalData = (PGPLiteralData) plainObj;
                InputStream literalStream = literalData.getInputStream();
                
                byte[] decryptedBytes = literalStream.readAllBytes();
                String decrypted = new String(decryptedBytes, StandardCharsets.UTF_8);
                
                log.debug("[CryptoUtil] 복호화 성공. 결과 길이: {}", decrypted.length());
                return decrypted;
            } else {
                throw new IllegalStateException("예상치 못한 PGP 객체 타입: " + plainObj.getClass().getName());
            }

        } catch (Exception e) {
            log.error("[CryptoUtil] 복호화 실패. 데이터 프리픽스: {}..., 오류: {}", 
                    encryptedData.substring(0, Math.min(50, encryptedData.length())), 
                    e.getMessage(), e);
            throw new RuntimeException("복호화 실패: " + e.getMessage(), e);
        }
    }

    /**
     * PostgreSQL bytea 형식 파싱
     * 
     * PostgreSQL bytea는 여러 형식으로 표현될 수 있음:
     * 1. Hex format: \x4142434445... (가장 흔한 형식)
     * 2. Escape format: \001\002\003...
     * 3. Raw bytes: 바이너리 데이터 그대로
     * 
     * @param byteaString PostgreSQL bytea 문자열
     * @return 바이트 배열
     */
    private byte[] parsePostgresByteA(String byteaString) {
        if (byteaString.startsWith("\\x")) {
            // Hex format: \x4142434445...
            String hexString = byteaString.substring(2);
            return hexStringToByteArray(hexString);
        } else if (byteaString.startsWith("\\")) {
            // Escape format: 복잡하므로 PostgreSQL JDBC 드라이버의 PGbytea 클래스 사용 권장
            // 현재는 간단히 UTF-8 바이트로 변환
            return byteaString.getBytes(StandardCharsets.UTF_8);
        } else {
            // Raw bytes 또는 이미 디코딩된 데이터
            return byteaString.getBytes(StandardCharsets.ISO_8859_1);
        }
    }

    /**
     * Hex 문자열을 바이트 배열로 변환
     * 
     * @param hex Hex 문자열 (예: "414243" -> byte[]{0x41, 0x42, 0x43})
     * @return 바이트 배열
     */
    private byte[] hexStringToByteArray(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * 데이터가 암호화되어 있는지 확인
     * 
     * @param data 확인할 데이터
     * @return 암호화 여부
     */
    public boolean isEncrypted(String data) {
        if (data == null || data.isEmpty()) {
            return false;
        }
        
        // PostgreSQL bytea hex format 확인
        if (data.startsWith("\\x")) {
            return true;
        }
        
        // 일반적으로 암호화된 데이터는 길이가 길고 특수 문자가 많음
        // 실제 평문과 구분하기 위한 휴리스틱
        return data.length() > 100 && containsBinaryCharacters(data);
    }

    /**
     * 문자열에 바이너리 문자가 포함되어 있는지 확인
     */
    private boolean containsBinaryCharacters(String data) {
        for (char c : data.toCharArray()) {
            if (c < 32 && c != '\n' && c != '\r' && c != '\t') {
                return true;
            }
        }
        return false;
    }

    /**
     * 안전한 복호화 (복호화 실패 시 원본 반환)
     * 
     * 로깅이나 테스트 환경에서 사용
     * 
     * @param encryptedData 암호화된 데이터
     * @return 복호화된 데이터 (실패 시 원본)
     */
    public String decryptSafe(String encryptedData) {
        try {
            return decrypt(encryptedData);
        } catch (Exception e) {
            log.warn("[CryptoUtil] 복호화 실패, 원본 반환. 오류: {}", e.getMessage());
            return encryptedData;
        }
    }

    /**
     * 복호화 활성화 여부 확인
     * 
     * @return 복호화 활성화 여부
     */
    public boolean isEnabled() {
        return enabled;
    }
}
