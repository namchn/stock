package com.nc.stock.project.stock.dto;

//순수자바코드로 만
public class StockInfoDto {
    private String  id;
    private String  date;  //날짜
    private Double  usableCash; //사용가능현금
    //private Double  fixedCash;  // 현금으로 남겨두는 금액
    
	private Double  minSignalPrice; // 신호최저값
    private Double  maxSignalPrice; // 신호최고값
    private Double  averageSignalPrice; // 이평선산출가격
	private String  signalType; // 신호 종류
    private String  preSignal;// 직전 신호
    private String  nowSignal;// 현재 신호 매수신호 매도신호 
    private String  depositMargin;// 매수증거금
    private String  ticker;// 티커
    private String  tradeOffice;// 거래소 종목
    
    private String  orderType;// 매수방법
    private String  orderStock;// 매수가능 갯수
    private Double  orderPrice;// 매수단가
    
    private String  sellingType;// 매도방법
    private Double  averagePrice;// 매수한 단가
    private String  positionStock;// 매도가능 갯수
    private Double  sellPrice;// 매도단가
    
    private Double  nowPrice;// 현재가격

    private Double  totalStockAsset;// 주식 총액
    
    // 기본 생성자, Getter, Setter 필요 (Jackson 역직렬화용)
    public StockInfoDto() {}
    
    public StockInfoDto(String id, String date, Double usableCash) {
        this.id = id;
        this.date = date;
        this.usableCash = usableCash;
    }

    /**
	 * @param id
	 * @param date
	 * @param usableCash
	 * @param signalType
	 * @param preSignal
	 * @param nowSignal
	 * @param depositMargin
	 * @param ticker
	 * @param tradeOffice
	 * @param orderType
	 * @param orderStock
	 * @param orderPrice
	 * @param sellingType
	 * @param averagePrice
	 * @param positionStock
	 * @param sellPrice
	 */
	public StockInfoDto(String id, String date, Double usableCash,Double minSignalPrice,Double maxSignalPrice,Double averageSignalPrice, String signalType, String preSignal,
			String nowSignal, String depositMargin, String ticker, String tradeOffice, String orderType,
			String orderStock, Double orderPrice, String sellingType, Double averagePrice, String positionStock,
			Double sellPrice,Double nowPrice,Double totalStockAsset) {
		super();
		this.id = id;
		this.date = date;
		this.usableCash = usableCash;
		this.minSignalPrice  = minSignalPrice;
		this.maxSignalPrice  = maxSignalPrice;
		this.averageSignalPrice  = averageSignalPrice;
		this.signalType = signalType;
		this.preSignal = preSignal;
		this.nowSignal = nowSignal;
		this.depositMargin = depositMargin;
		this.ticker = ticker;
		this.tradeOffice = tradeOffice;
		this.orderType = orderType;
		this.orderStock = orderStock;
		this.orderPrice = orderPrice;
		this.sellingType = sellingType;
		this.averagePrice = averagePrice;
		this.positionStock = positionStock;
		this.sellPrice = sellPrice;
		this.nowPrice = nowPrice;
		this.totalStockAsset = totalStockAsset;
	}

	public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public Double getUsableCash() { return usableCash; }
    public void setUsableCash(Double usableCash) { this.usableCash = usableCash; }
    public Double getMinSignalPrice() {return minSignalPrice;}
	public void setMinSignalPrice(Double minSignalPrice) {this.minSignalPrice = minSignalPrice;}
	public Double getMaxSignalPrice() {return maxSignalPrice;}
	public void setMaxSignalPrice(Double maxSignalPrice) {this.maxSignalPrice = maxSignalPrice;}
	public Double getAverageSignalPrice() {return averageSignalPrice;}
	public void setAverageSignalPrice(Double averageSignalPrice) {this.averageSignalPrice = averageSignalPrice;}
	public String getSignalType() {return signalType;}
    public void setSignalType(String signalType) {this.signalType = signalType;}
    public String getPreSignal() {return preSignal;}
    public void setPreSignal(String preSignal) {this.preSignal = preSignal;}
    public String getNowSignal() {return nowSignal;}
    public void setNowSignal(String nowSignal) {this.nowSignal = nowSignal;}
    public String getDepositMargin() {return depositMargin;}
    public void setDepositMargin(String depositMargin) {this.depositMargin = depositMargin;}
    public String getTicker() {return ticker;}
    public void setTicker(String ticker) {this.ticker = ticker;}
    public String getTradeOffice() {return tradeOffice;}
    public void setTradeOffice(String tradeOffice) {this.tradeOffice = tradeOffice;}
    public String getOrderType() {return orderType;}
    public void setOrderType(String orderType) {this.orderType = orderType;}
    public String getOrderStock() {return orderStock;}
    public void setOrderStock(String orderStock) {this.orderStock = orderStock;}
    public Double getOrderPrice() {return orderPrice;}
    public void setOrderPrice(Double orderPrice) {this.orderPrice = orderPrice;}
    public String getSellingType() {return sellingType;}
    public void setSellingType(String sellingType) {this.sellingType = sellingType;}
    public Double getAveragePrice() {return averagePrice;}
    public void setAveragePrice(Double averagePrice) {this.averagePrice = averagePrice;}
    public String getPositionStock() {return positionStock;}
    public void setPositionStock(String positionStock) {this.positionStock = positionStock;}
    public Double getSellPrice() {return sellPrice;}
    public void setSellPrice(Double sellPrice) {this.sellPrice = sellPrice;}
    public Double getNowPrice() {return nowPrice;}
    public void setNowPrice(Double nowPrice) {this.nowPrice = nowPrice;}
    public Double getTotalStockAsset() {return totalStockAsset;}
    public void setTotalStockAsset(Double totalStockAsset) {this.totalStockAsset = totalStockAsset;}
    
}
