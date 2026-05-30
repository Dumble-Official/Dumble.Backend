using CloudinaryDotNet;
using MassTransit;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Dumble.PostService.Application.Contracts;
using Dumble.PostService.Infrastructure.Authentication;
using Dumble.PostService.Infrastructure.Blobs;
using Dumble.PostService.Infrastructure.Messaging.Consumers;
using Dumble.PostService.Infrastructure.Persistence;
using Dumble.PostService.Infrastructure.Persistence.Repositories;
using Dumble.SharedKernel.Contracts;

namespace Dumble.PostService.Infrastructure;

public static class DependencyInjection
{
    public static IServiceCollection AddInfrastructure(this IServiceCollection services, IConfiguration configuration)
    {
        var connectionString = configuration.GetConnectionString("PostDb")
            ?? throw new InvalidOperationException("ConnectionStrings:PostDb is required");
        services.AddDbContext<PostDbContext>(options => options
            .UseNpgsql(connectionString));

        services.AddScoped<IPostRepository, PostRepository>();
        services.AddScoped<IReactionRepository, ReactionRepository>();
        services.AddScoped<ICommentRepository, CommentRepository>();
        services.AddScoped<ICommentReactionRepository, CommentReactionRepository>();
        services.AddScoped<IHashtagRepository, HashtagRepository>();

        var cloudinaryUrl = configuration["Cloudinary:Url"]
            ?? throw new InvalidOperationException("Cloudinary:Url is required");
        services.AddSingleton(new Cloudinary(cloudinaryUrl));
        services.AddScoped<IFileService, CloudinaryFileService>();

        services.AddHttpContextAccessor();
        services.AddScoped<ILoggedInUserService, LoggedInUserService>();

        services.AddMassTransit(x =>
        {
            // Account deletion arrives from the Java Auth service as raw JSON on the shared
            // dumble.events topic exchange, so it is wired explicitly and kept out of the
            // convention-based ConfigureEndpoints.
            x.AddConsumer<AccountDeletedConsumer>().ExcludeFromConfigureEndpoints();

            x.UsingRabbitMq((context, cfg) =>
            {
                cfg.Host(configuration["RabbitMQ:Host"] ?? "localhost", "/", h =>
                {
                    h.Username(configuration["RabbitMQ:Username"] ?? "guest");
                    h.Password(configuration["RabbitMQ:Password"] ?? "guest");
                });
                cfg.ConfigureEndpoints(context);

                cfg.ReceiveEndpoint("post-service.account-deleted", e =>
                {
                    // Raw JSON only for DESERIALIZING the inbound Java event. Using the full
                    // UseRawJsonSerializer would also make the PostDeletedEvent this consumer
                    // publishes go out as raw JSON (no envelope), which the recommendation
                    // service's PostDeleted consumer then can't deserialize.
                    e.UseRawJsonDeserializer();
                    e.UseMessageRetry(r => r.Interval(3, TimeSpan.FromSeconds(5)));
                    e.Bind("dumble.events", b =>
                    {
                        b.ExchangeType = "topic";
                        b.Durable = true;
                        b.RoutingKey = "account.deleted";
                    });
                    e.ConfigureConsumer<AccountDeletedConsumer>(context);
                });
            });
        });

        return services;
    }
}
