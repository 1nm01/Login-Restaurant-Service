package khana;


import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.google.gson.Gson;
import io.jsonwebtoken.*;
import khana.entity.ApiResponse;
import khana.entity.Credentials;

import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.util.*;
public class Handler implements RequestHandler<APIGatewayProxyRequestEvent, ApiResponse> {

    private static final AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().withRegion(Regions.AP_SOUTH_1).build();
    private static final DynamoDBMapper dynamoDBMapper = new DynamoDBMapper(client);
    @Override
    public ApiResponse handleRequest(APIGatewayProxyRequestEvent apiGatewayProxyRequestEvent, Context context) {
        String body = apiGatewayProxyRequestEvent.getBody();
        Gson gson = new Gson();
        Credentials credentials = gson.fromJson(body, Credentials.class);
        Map<String, AttributeValue> eav = new HashMap<String, AttributeValue>();
        eav.put(":v1", new AttributeValue().withS(credentials.getRestId()));

        DynamoDBQueryExpression<Credentials> queryExpression = new DynamoDBQueryExpression<Credentials>()
                .withKeyConditionExpression("RestaurantId = :v1")
                .withExpressionAttributeValues(eav);
        PaginatedQueryList<Credentials> query = dynamoDBMapper.query(Credentials.class, queryExpression);

        Iterator<Credentials> iterator = query.stream().iterator();
        Credentials dbCredential = null;

        if(iterator.hasNext()){
            dbCredential = iterator.next();
        }
        Map<String,String> headers = Map.of("content-type", "application/json");
        if(dbCredential == null){
            return new ApiResponse(201, headers, "{\"message\":\"No such Restaurant\"}");
        }
        if(!dbCredential.getPassword().equals(credentials.getPassword())){
            return new ApiResponse(201, headers, "{\"message\":\"Wrong password\"}");
        }
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.YEAR, 2); // to get previous year add -1
        Date expiration = cal.getTime();
        String secret = "ThisiskhanaappthisisthuglifeNeverunderestimateme";
        Key hmacKey = new SecretKeySpec(Base64.getDecoder().decode(secret),
                SignatureAlgorithm.HS256.getJcaName());
        String token = Jwts.builder().claim("restId", credentials.getRestId()).signWith(hmacKey).expiration(expiration).compact();

        return new ApiResponse(201, headers, "{\"token\" : \"" + token + "\" }");
    }
}
