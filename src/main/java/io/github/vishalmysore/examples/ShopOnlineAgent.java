package io.github.vishalmysore.examples;

import com.t4a.annotations.Action;
import com.t4a.annotations.Agent;
import com.t4a.api.ActionRisk;

@Agent(groupName = "ShopOnlineAgent", groupDescription = "An AI agent that helps users shop online by finding products, comparing prices, and making recommendations")
public class ShopOnlineAgent {

    @Action(description = "Find a product online by name and return the best price and store information", riskLevel = ActionRisk.LOW )
    public String findProduct(String productName) {
        // Simulate finding a product online
        return "Found product: " + productName + " at $19.99 on ExampleStore.com";
    }

    @Action(description = "Compare two products and return a recommendation based on price and features", riskLevel = ActionRisk.MEDIUM)
    public String compareProducts(String product1, String product2) {
        // Simulate comparing two products
        return "Comparing " + product1 + " and " + product2 + ": " +
               product1 + " is cheaper at $19.99, but " + product2 + " has better features.";
    }

        @Action(description = "Get recommendations for similar products based on a given product name", riskLevel = ActionRisk.LOW)
    public String getRecommendations(String productName) {
        // Simulate getting product recommendations
        return "Based on " + productName + ", you might also like: " +
               "Product A, Product B, Product C.";
        }

        @Action(description = "Order a product online by providing the product name and store information", riskLevel = ActionRisk.HIGH)
        public  String orderOnline(String productName, String store) {
            // Simulate ordering a product online
            return "Ordered " + productName + " from " + store + ". Estimated delivery: 3-5 business days.";
        }
}
