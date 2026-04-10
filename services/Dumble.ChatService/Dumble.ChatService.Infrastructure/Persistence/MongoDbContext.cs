using MongoDB.Driver;
using Dumble.ChatService.Domain.Models;

namespace Dumble.ChatService.Infrastructure.Persistence;

public class MongoDbContext
{
    private readonly IMongoDatabase _database;

    public MongoDbContext(IMongoClient client, string databaseName)
    {
        _database = client.GetDatabase(databaseName);
        ConfigureIndexes();
    }

    public IMongoCollection<Conversation> Conversations => _database.GetCollection<Conversation>("conversations");
    public IMongoCollection<Message> Messages => _database.GetCollection<Message>("messages");

    private void ConfigureIndexes()
    {
        // Conversations: user's conversation list sorted by last activity
        Conversations.Indexes.CreateOne(new CreateIndexModel<Conversation>(
            Builders<Conversation>.IndexKeys
                .Ascending("Participants.UserId")
                .Descending(c => c.UpdatedAt)));

        // Messages: conversation messages sorted by time
        Messages.Indexes.CreateOne(new CreateIndexModel<Message>(
            Builders<Message>.IndexKeys
                .Ascending(m => m.ConversationId)
                .Descending(m => m.CreatedAt)));
    }
}
