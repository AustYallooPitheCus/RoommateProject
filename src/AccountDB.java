// AccountDB.java
// 네이버 간편로그인을 흉내 내는 테스트 계정 20개 (남 10, 여 10)
// 시작 화면에는 카카오, 네이버 버튼이 다 있지만 지금은 네이버로 로그인하는 경우만 만듦
// 실제 간편로그인은 Swing 소켓 앱에 붙일 수 없어서, 서버 안에 "로그인 제공자가 가진 정보"를 넣어둠
//
// 주민번호는 전부 테스트용 가짜 번호
//   앞 6자리: 생년월일 (YYMMDD)
//   뒷 7자리: 성별 숫자 1자리 + 000001 ~ 000020 순번  -> 실제 누군가의 번호와 겹칠 일이 없게 일부러 이렇게 만듦
//   성별 숫자: 1900년대생 남 1 / 여 2, 2000년대생 남 3 / 여 4
//
// 우리 서버(RoommateServer)는 이 주민번호를 그대로 쓰거나 저장하지 않고,
// 로그인할 때 이름, 생년월일, 성별만 뽑아서 씀

public class AccountDB {

    // 계정 하나
    public static class Account {
        public final String id;        // 간편로그인 계정 아이디
        public final String realName;  // 실명
        public final String rrn;       // 주민번호 (테스트용)
        public final String studentId; // 학번 (테스트용, 임의로 만든 7자리)

        Account(String id, String realName, String rrn, String studentId) {
            this.id = id;
            this.realName = realName;
            this.rrn = rrn;
            this.studentId = studentId;
        }

        // 뒷자리 첫 숫자. "980314-1000001" -> '1'
        public char genderDigit() {
            return rrn.charAt(7);
        }

        // "M" 또는 "F"
        public String gender() {
            char d = genderDigit();
            return (d == '1' || d == '3') ? "M" : "F";
        }

        // "1998-03-14" 형식. 성별 숫자로 1900년대/2000년대를 구분
        public String birthDate() {
            char d = genderDigit();
            String century = (d == '1' || d == '2') ? "19" : "20";
            return century + rrn.substring(0, 2) + "-" + rrn.substring(2, 4) + "-" + rrn.substring(4, 6);
        }

        // 네이버 로그인으로 받는 휴대폰 번호 (테스트용이라 010-0000-00번호)
        public String phone() {
            return "010-0000-00" + id.substring(id.length() - 2);
        }

        // 계정 고르는 화면에 보여줄 이름. 가운데 글자를 가림 (김민준 -> 김*준)
        public String maskedName() {
            if (realName.length() <= 2) return realName.charAt(0) + "*";
            return realName.charAt(0) + "*" + realName.substring(2);
        }
    }

    public static final Account[] ACCOUNTS = {
        // 남자 10명
        new Account("naver01", "김민준", "980314-1000001", "2271301"),
        new Account("naver02", "이도윤", "010722-3000002", "2471302"),
        new Account("naver03", "박서준", "990105-1000003", "2171303"),
        new Account("naver04", "최예준", "021118-3000004", "2571304"),
        new Account("naver05", "정시우", "970830-1000005", "2271305"),
        new Account("naver06", "강하준", "040226-3000006", "2471306"),
        new Account("naver07", "조지호", "000909-3000007", "2171307"),
        new Account("naver08", "윤주원", "961201-1000008", "2271308"),
        new Account("naver09", "장지후", "030417-3000009", "2371309"),
        new Account("naver10", "임건우", "990621-1000010", "2571310"),
        // 여자 10명
        new Account("naver11", "김서연", "010312-4000011", "2571311"),
        new Account("naver12", "이서윤", "980725-2000012", "2171312"),
        new Account("naver13", "박지우", "030108-4000013", "2471313"),
        new Account("naver14", "최하은", "970514-2000014", "2371314"),
        new Account("naver15", "정민서", "021030-4000015", "2571315"),
        new Account("naver16", "강지민", "001224-4000016", "2371316"),
        new Account("naver17", "조윤서", "990219-2000017", "2471317"),
        new Account("naver18", "윤채원", "040607-4000018", "2471318"),
        new Account("naver19", "장지아", "960911-2000019", "2271319"),
        new Account("naver20", "임다은", "011103-4000020", "2571320"),
    };

    // 아이디로 찾기. 없으면 null
    public static Account find(String id) {
        for (Account a : ACCOUNTS) {
            if (a.id.equals(id)) return a;
        }
        return null;
    }
}
