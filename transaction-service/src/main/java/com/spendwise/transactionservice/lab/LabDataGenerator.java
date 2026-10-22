package com.spendwise.transactionservice.lab;

import com.spendwise.transactionservice.domain.TransactionType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Production Debugging Lab — generates a realistic Indian retail-banking
 * ledger for synthetic users, standing in for the masked copy of production
 * data a bank's UAT or performance environment would hold.
 *
 * <p>Each user gets a persona (salary band, employer, city, rent, loan, SIP,
 * subscriptions) and, per month: a salary credit on the 1st (with an April
 * appraisal), fixed debits (rent, EMI, SIP, utility bills, subscriptions),
 * 20–55 UPI card-less spends spread with a post-payday and month-end bias,
 * ATM withdrawals, occasional travel bookings and refunds. Descriptions follow
 * bank-statement conventions ({@code UPI/<merchant>/<RRN>/<app>},
 * {@code ACH/...}, {@code NEFT/...}, {@code BBPS/...}).
 *
 * <p>Output is deterministic for a given seed, user index and date window, so
 * an incident can be reproduced against exactly the same data.
 */
public final class LabDataGenerator {

    /** Upper-end estimate, used for progress percentages so they never overshoot 100%. */
    public static final int ESTIMATED_ROWS_PER_USER_MONTH = 48;

    /** Measured average (about 39 rows per user per month), used when describing sizes to people. */
    public static final int TYPICAL_ROWS_PER_USER_MONTH = 39;

    private static final BigDecimal APPRAISAL_FACTOR = new BigDecimal("1.08");
    private static final String[] UPI_APPS = {"GPay", "PhonePe", "Paytm", "BHIM", "CRED"};
    private static final String[] EMPLOYERS = {
            "INFOSYS LTD", "TATA CONSULTANCY SERVICES", "WIPRO LTD", "HCL TECHNOLOGIES LTD",
            "ACCENTURE SOLUTIONS PVT LTD", "CAPGEMINI TECHNOLOGY SERVICES", "PERSISTENT SYSTEMS LTD",
            "BAJAJ FINSERV LTD", "COGNIZANT TECHNOLOGY SOLUTIONS", "L&T TECHNOLOGY SERVICES",
            "TECH MAHINDRA LTD", "KPIT TECHNOLOGIES LTD", "MAHINDRA & MAHINDRA LTD", "ASIAN PAINTS LTD"};
    private static final String[] LENDERS = {
            "HDFC BANK", "ICICI BANK", "BAJAJ FINANCE", "STATE BANK OF INDIA", "AXIS BANK", "TATA CAPITAL"};
    private static final String[] AMCS = {
            "SBI MUTUAL FUND", "HDFC MUTUAL FUND", "AXIS MUTUAL FUND", "PARAG PARIKH MF",
            "ICICI PRUDENTIAL MF", "NIPPON INDIA MF"};
    private static final String[] MOBILE_OPERATORS = {"JIO PREPAID", "AIRTEL PREPAID", "VI PREPAID"};
    private static final String[] BROADBAND = {"ACT FIBERNET", "AIRTEL XSTREAM", "JIOFIBER", "HATHWAY"};
    private static final String[] LANDLORDS = {
            "MR R KULKARNI", "MRS S DESHPANDE", "MR A IYER", "MR V REDDY", "MRS P SHARMA",
            "MR K NAIR", "MR M JOSHI", "MRS L GUPTA", "MR S PATIL", "MR T MENON"};
    private static final String[] BANKS = {"HDFC", "ICICI", "SBI", "AXIS", "KOTAK"};

    private static final List<City> CITIES = List.of(
            new City("PUNE", "MSEDCL", "PUNE METRO"),
            new City("MUMBAI", "ADANI ELECTRICITY", "MUMBAI METRO ONE"),
            new City("BENGALURU", "BESCOM", "NAMMA METRO"),
            new City("HYDERABAD", "TSSPDCL", "HYDERABAD METRO"),
            new City("CHENNAI", "TANGEDCO", "CHENNAI METRO"),
            new City("DELHI", "BSES RAJDHANI", "DELHI METRO"));

    private static final List<Spend> DAILY_SPENDS = List.of(
            new Spend(LabCategory.FOOD, 30, List.of(
                    new Merchant("SWIGGY", 150, 950), new Merchant("ZOMATO", 150, 1100),
                    new Merchant("DOMINOS PIZZA", 300, 900), new Merchant("STARBUCKS", 250, 750),
                    new Merchant("CHAAYOS", 120, 450), new Merchant("BURGER KING", 180, 650))),
            new Spend(LabCategory.GROCERIES, 22, List.of(
                    new Merchant("BIGBASKET", 400, 3500), new Merchant("BLINKIT", 120, 1500),
                    new Merchant("ZEPTO", 120, 1400), new Merchant("DMART", 600, 5000),
                    new Merchant("RELIANCE SMART", 300, 3500), new Merchant("SWIGGY INSTAMART", 150, 1300))),
            new Spend(LabCategory.TRANSPORT, 18, List.of(
                    new Merchant("UBER INDIA", 120, 900), new Merchant("OLA CABS", 100, 800),
                    new Merchant("RAPIDO", 40, 250), new Merchant("PAYTM FASTAG", 200, 1000))),
            new Spend(LabCategory.FUEL, 6, List.of(
                    new Merchant("HP PETROL PUMP", 300, 3000), new Merchant("INDIAN OIL", 300, 3000),
                    new Merchant("BHARAT PETROLEUM", 300, 3000))),
            new Spend(LabCategory.SHOPPING, 10, List.of(
                    new Merchant("AMAZON PAY", 199, 6000), new Merchant("FLIPKART", 199, 8000),
                    new Merchant("MYNTRA", 499, 4500), new Merchant("NYKAA", 299, 3000),
                    new Merchant("AJIO", 399, 3500))),
            new Spend(LabCategory.ENTERTAINMENT, 4, List.of(
                    new Merchant("BOOKMYSHOW", 250, 1500), new Merchant("PVR INOX", 300, 1400))),
            new Spend(LabCategory.HEALTH, 5, List.of(
                    new Merchant("APOLLO PHARMACY", 120, 2500), new Merchant("PHARMEASY", 200, 2500),
                    new Merchant("PRACTO", 300, 900), new Merchant("TATA 1MG", 150, 2000))));

    private static final int DAILY_SPEND_TOTAL_WEIGHT =
            DAILY_SPENDS.stream().mapToInt(Spend::weight).sum();

    private final long seed;
    private final int months;
    private final LocalDate lastDate;

    /**
     * @param seed     base random seed; same seed, same data
     * @param months   number of calendar months, ending with the month of {@code lastDate}
     * @param lastDate latest transaction date to generate (inclusive)
     */
    public LabDataGenerator(long seed, int months, LocalDate lastDate) {
        if (months < 1) {
            throw new IllegalArgumentException("months must be >= 1");
        }
        this.seed = seed;
        this.months = months;
        this.lastDate = lastDate;
    }

    public LocalDate firstDate() {
        return lastDate.withDayOfMonth(1).minusMonths(months - 1L);
    }

    public LocalDate lastDate() {
        return lastDate;
    }

    /** Emits every ledger entry for one lab user, oldest month first. */
    public void generateUser(int userIndex, Consumer<LabTransactionRow> sink) {
        SplittableRandom rnd = new SplittableRandom(mix(seed, userIndex));
        Persona persona = Persona.random(rnd);
        UUID userId = LabIds.userId(userIndex);
        YearMonth firstMonth = YearMonth.from(firstDate());
        YearMonth lastMonth = YearMonth.from(lastDate);

        BigDecimal salary = persona.baseSalary();
        for (YearMonth month = firstMonth; !month.isAfter(lastMonth); month = month.plusMonths(1)) {
            if (month.getMonth() == Month.APRIL && month.isAfter(firstMonth)) {
                salary = salary.multiply(APPRAISAL_FACTOR).setScale(0, RoundingMode.HALF_UP);
            }
            MonthEmitter emitter = new MonthEmitter(userId, month, rnd, sink);
            fixedEntries(persona, salary, month, rnd, emitter);
            dailySpends(persona, month, rnd, emitter);
        }
    }

    private void fixedEntries(Persona p, BigDecimal salary, YearMonth month, SplittableRandom rnd,
                              MonthEmitter out) {
        String monthLabel = month.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase(Locale.ROOT)
                + "-" + month.getYear();

        out.emit(1, 9, LabCategory.SALARY, salary.setScale(2, RoundingMode.HALF_UP), TransactionType.INCOME,
                "NEFT/SALARY " + monthLabel + "/" + p.employer());

        if (p.rent() != null) {
            out.emit(3 + rnd.nextInt(3), 10, LabCategory.RENT, p.rent(), TransactionType.EXPENSE,
                    "IMPS/RENT " + monthLabel + "/" + p.landlord());
        }
        if (p.emi() != null) {
            out.emit(p.emiDay(), 6, LabCategory.LOAN_EMI, p.emi(), TransactionType.EXPENSE,
                    "ACH/" + p.lender() + "/LOAN EMI " + monthLabel);
        }
        if (p.sip() != null) {
            out.emit(7, 7, LabCategory.INVESTMENTS, p.sip(), TransactionType.EXPENSE,
                    "ACH/SIP/" + p.amc());
        }

        BigDecimal electricity = amount(rnd, 700, 3200);
        if (month.getMonth().getValue() >= 4 && month.getMonth().getValue() <= 6) {
            electricity = electricity.multiply(new BigDecimal("1.6")).setScale(0, RoundingMode.HALF_UP);
        }
        out.emit(12 + rnd.nextInt(7), 20, LabCategory.BILLS, electricity.setScale(2, RoundingMode.HALF_UP),
                TransactionType.EXPENSE, "BBPS/" + p.city().electricityBoard() + "/ELECTRICITY BILL");
        out.emit(p.mobileDay(), 21, LabCategory.BILLS, p.mobilePlan(), TransactionType.EXPENSE,
                "UPI/" + p.mobileOperator() + "/" + rrn(rnd) + "/" + p.upiApp());
        if (p.broadband() != null) {
            out.emit(p.broadbandDay(), 11, LabCategory.BILLS, p.broadbandPlan(), TransactionType.EXPENSE,
                    "BBPS/" + p.broadband() + "/BROADBAND BILL");
        }
        if (p.netflix() != null) {
            out.emit(p.subscriptionDay(), 2, LabCategory.ENTERTAINMENT, p.netflix(), TransactionType.EXPENSE,
                    "SI/NETFLIX.COM/SUBSCRIPTION");
        }
        if (p.spotify()) {
            out.emit(p.subscriptionDay(), 2, LabCategory.ENTERTAINMENT, new BigDecimal("119.00"),
                    TransactionType.EXPENSE, "SI/SPOTIFY INDIA/PREMIUM");
        }

        int atmWithdrawals = 1 + rnd.nextInt(3);
        for (int i = 0; i < atmWithdrawals; i++) {
            BigDecimal cash = BigDecimal.valueOf(500L * (1 + rnd.nextInt(20))).setScale(2, RoundingMode.HALF_UP);
            out.emit(1 + rnd.nextInt(28), 10 + rnd.nextInt(10), LabCategory.CASH, cash, TransactionType.EXPENSE,
                    "ATM WDL/" + p.bank() + " ATM/" + p.city().name());
        }

        if (rnd.nextInt(100) < 40) {
            out.emit(1 + rnd.nextInt(28), 22, LabCategory.TRAVEL, amount(rnd, 300, 4500), TransactionType.EXPENSE,
                    "UPI/IRCTC/" + rrn(rnd) + "/" + p.upiApp());
        }
        if (rnd.nextInt(100) < 8) {
            String carrier = rnd.nextBoolean() ? "MAKEMYTRIP" : "INDIGO AIRLINES";
            out.emit(1 + rnd.nextInt(28), 23, LabCategory.TRAVEL, amount(rnd, 3000, 18000), TransactionType.EXPENSE,
                    "UPI/" + carrier + "/" + rrn(rnd) + "/" + p.upiApp());
        }
        if (rnd.nextInt(100) < 25) {
            boolean cashback = rnd.nextBoolean();
            out.emit(1 + rnd.nextInt(28), 14, LabCategory.REFUNDS,
                    cashback ? amount(rnd, 10, 300) : amount(rnd, 199, 2500), TransactionType.INCOME,
                    cashback ? "CASHBACK/CRED/REWARD" : "REFUND/AMAZON PAY/" + rrn(rnd));
        }
    }

    private void dailySpends(Persona p, YearMonth month, SplittableRandom rnd, MonthEmitter out) {
        int count = p.monthlySpends() - 5 + rnd.nextInt(11);
        int daysInMonth = month.lengthOfMonth();
        double[] dayWeights = new double[daysInMonth];
        double totalWeight = 0;
        for (int day = 1; day <= daysInMonth; day++) {
            double weight = 1.0;
            if (day <= 7) {
                weight *= 1.6;
            }
            if (day > daysInMonth - 3) {
                weight *= 1.4;
            }
            DayOfWeek dow = month.atDay(day).getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                weight *= 1.3;
            }
            dayWeights[day - 1] = weight;
            totalWeight += weight;
        }

        for (int i = 0; i < count; i++) {
            int day = pickDay(dayWeights, totalWeight, rnd);
            Spend spend = pickSpend(rnd);
            Merchant merchant = spend.category() == LabCategory.TRANSPORT && rnd.nextInt(100) < 25
                    ? new Merchant(p.city().metro(), 20, 80)
                    : spend.merchants().get(rnd.nextInt(spend.merchants().size()));
            BigDecimal value = amount(rnd, merchant.min(), merchant.max());
            if (rnd.nextInt(100) < 35) {
                value = value.add(BigDecimal.valueOf(rnd.nextInt(100), 2));
            }
            out.emit(day, spendHour(rnd), spend.category(), value, TransactionType.EXPENSE,
                    "UPI/" + merchant.name() + "/" + rrn(rnd) + "/" + p.upiApp());
        }
    }

    private static int pickDay(double[] weights, double total, SplittableRandom rnd) {
        double target = rnd.nextDouble() * total;
        for (int i = 0; i < weights.length; i++) {
            target -= weights[i];
            if (target <= 0) {
                return i + 1;
            }
        }
        return weights.length;
    }

    private static Spend pickSpend(SplittableRandom rnd) {
        int target = rnd.nextInt(DAILY_SPEND_TOTAL_WEIGHT);
        for (Spend spend : DAILY_SPENDS) {
            target -= spend.weight();
            if (target < 0) {
                return spend;
            }
        }
        return DAILY_SPENDS.get(0);
    }

    private static int spendHour(SplittableRandom rnd) {
        int bucket = rnd.nextInt(100);
        if (bucket < 15) {
            return 8 + rnd.nextInt(3);
        }
        if (bucket < 45) {
            return 12 + rnd.nextInt(3);
        }
        if (bucket < 90) {
            return 18 + rnd.nextInt(4);
        }
        return 22 + rnd.nextInt(2);
    }

    static BigDecimal amount(SplittableRandom rnd, int min, int max) {
        return BigDecimal.valueOf(min + (long) rnd.nextInt(max - min + 1)).setScale(2, RoundingMode.HALF_UP);
    }

    private static String rrn(SplittableRandom rnd) {
        return String.valueOf(rnd.nextLong(100_000_000_000L, 1_000_000_000_000L));
    }

    private static long mix(long seed, int userIndex) {
        long z = seed * 0x9E3779B97F4A7C15L + userIndex;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private final class MonthEmitter {
        private final UUID userId;
        private final YearMonth month;
        private final SplittableRandom rnd;
        private final Consumer<LabTransactionRow> sink;

        MonthEmitter(UUID userId, YearMonth month, SplittableRandom rnd, Consumer<LabTransactionRow> sink) {
            this.userId = userId;
            this.month = month;
            this.rnd = rnd;
            this.sink = sink;
        }

        void emit(int dayOfMonth, int hour, LabCategory category, BigDecimal amount, TransactionType type,
                  String description) {
            LocalDate date = month.atDay(Math.min(dayOfMonth, month.lengthOfMonth()));
            if (date.isBefore(firstDate()) || date.isAfter(lastDate)) {
                return;
            }
            LocalDateTime createdAt = date.atTime(hour, rnd.nextInt(60), rnd.nextInt(60));
            UUID id = new UUID(
                    (rnd.nextLong() & 0xFFFF_FFFF_FFFF_0FFFL) | 0x0000_0000_0000_4000L,
                    (rnd.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L);
            sink.accept(new LabTransactionRow(id, userId, category, amount, type, description, date, createdAt));
        }
    }

    private record City(String name, String electricityBoard, String metro) {
    }

    private record Merchant(String name, int min, int max) {
    }

    private record Spend(LabCategory category, int weight, List<Merchant> merchants) {
    }

    private record Persona(
            BigDecimal baseSalary,
            String employer,
            City city,
            String bank,
            String upiApp,
            BigDecimal rent,
            String landlord,
            BigDecimal emi,
            String lender,
            int emiDay,
            BigDecimal sip,
            String amc,
            String mobileOperator,
            BigDecimal mobilePlan,
            int mobileDay,
            String broadband,
            BigDecimal broadbandPlan,
            int broadbandDay,
            BigDecimal netflix,
            boolean spotify,
            int subscriptionDay,
            int monthlySpends) {

        static Persona random(SplittableRandom rnd) {
            int band = rnd.nextInt(100);
            long salary;
            int spends;
            if (band < 35) {
                salary = 25_000 + rnd.nextInt(20_001);
                spends = 22;
            } else if (band < 70) {
                salary = 45_000 + rnd.nextInt(45_001);
                spends = 32;
            } else if (band < 92) {
                salary = 90_000 + rnd.nextInt(90_001);
                spends = 40;
            } else {
                salary = 180_000 + rnd.nextInt(220_001);
                spends = 48;
            }
            BigDecimal base = BigDecimal.valueOf(salary / 100 * 100);

            BigDecimal rent = rnd.nextInt(100) < 60 ? percentOf(base, 20 + rnd.nextInt(16), 500) : null;
            BigDecimal emi = rnd.nextInt(100) < 45 ? percentOf(base, 8 + rnd.nextInt(18), 1) : null;
            BigDecimal sip = rnd.nextInt(100) < 50 ? percentOf(base, 5 + rnd.nextInt(11), 500) : null;
            BigDecimal[] mobilePlans = {new BigDecimal("299.00"), new BigDecimal("349.00"),
                    new BigDecimal("479.00"), new BigDecimal("719.00"), new BigDecimal("999.00")};
            BigDecimal[] broadbandPlans = {new BigDecimal("499.00"), new BigDecimal("699.00"),
                    new BigDecimal("999.00"), new BigDecimal("1199.00")};
            boolean hasBroadband = rnd.nextInt(100) < 55;
            BigDecimal netflix = rnd.nextInt(100) < 40
                    ? (rnd.nextBoolean() ? new BigDecimal("199.00") : new BigDecimal("649.00"))
                    : null;

            return new Persona(
                    base,
                    EMPLOYERS[rnd.nextInt(EMPLOYERS.length)],
                    CITIES.get(rnd.nextInt(CITIES.size())),
                    BANKS[rnd.nextInt(BANKS.length)],
                    UPI_APPS[rnd.nextInt(UPI_APPS.length)],
                    rent,
                    LANDLORDS[rnd.nextInt(LANDLORDS.length)],
                    emi,
                    LENDERS[rnd.nextInt(LENDERS.length)],
                    rnd.nextBoolean() ? 5 : 10,
                    sip,
                    AMCS[rnd.nextInt(AMCS.length)],
                    MOBILE_OPERATORS[rnd.nextInt(MOBILE_OPERATORS.length)],
                    mobilePlans[rnd.nextInt(mobilePlans.length)],
                    1 + rnd.nextInt(28),
                    hasBroadband ? BROADBAND[rnd.nextInt(BROADBAND.length)] : null,
                    broadbandPlans[rnd.nextInt(broadbandPlans.length)],
                    1 + rnd.nextInt(28),
                    netflix,
                    rnd.nextInt(100) < 30,
                    1 + rnd.nextInt(28),
                    spends);
        }

        private static BigDecimal percentOf(BigDecimal base, int percent, int roundTo) {
            long value = base.longValue() * percent / 100;
            long rounded = Math.max(roundTo, value / roundTo * roundTo);
            return BigDecimal.valueOf(rounded).setScale(2, RoundingMode.HALF_UP);
        }
    }
}
