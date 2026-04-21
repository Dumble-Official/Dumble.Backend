using CloudinaryDotNet;
using MassTransit;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Dumble.PostService.Application.Contracts;
using Dumble.PostService.Infrastructure.Authentication;
using Dumble.PostService.Infrastructure.Blobs;
using Dumble.PostService.Infrastructure.Persistence;
using Dumble.PostService.Infrastructure.Persistence.Repositories;
using Dumble.SharedKernel.Contracts;

namespace Dumble.PostService.Infrastructure;

public static class DependencyInjection
{
    public static IServiceCollection AddInfrastructure(this IServiceCollection services, IConfiguration configuration)
    {
        // PostgreSQL + EF Core
        services.AddDbContext<PostDbContext>(options =>
            options.UseNpgsql(configuration.GetConnectionString("PostDb")));

        // Repositories
        services.AddScoped<IPostRepository, PostRepository>();
        services.AddScoped<IReactionRepository, ReactionRepository>();
        services.AddScoped<ICommentRepository, CommentRepository>();
        services.AddScoped<ICommentReactionRepository, CommentReactionRepository>();
        services.AddScoped<IHashtagRepository, HashtagRepository>();

        // Cloudinary
        var cloudinaryUrl = configuration["Cloudinary:Url"];
        if (!string.IsNullOrEmpty(cloudinaryUrl))
        {
            var cloudinary = new Cloudinary(cloudinaryUrl);
            services.AddSingleton(cloudinary);
        }
        services.AddScoped<IFileService, CloudinaryFileService>();

        // Auth service HTTP client
        services.AddHttpContextAccessor();
        services.AddHttpClient<ILoggedInUserService, LoggedInUserService>(client =>
        {
            client.BaseAddress = new Uri(configuration["AuthService:BaseUrl"] ?? "http://localhost:8081");
        });

        // MassTransit + RabbitMQ
        services.AddMassTransit(x =>
        {
            x.UsingRabbitMq((context, cfg) =>
            {
                cfg.Host(configuration["RabbitMQ:Host"] ?? "localhost", "/", h =>
                {
                    h.Username(configuration["RabbitMQ:Username"] ?? "guest");
                    h.Password(configuration["RabbitMQ:Password"] ?? "guest");
                });

                cfg.ConfigureEndpoints(context);
            });
        });

        return services;
    }
}
