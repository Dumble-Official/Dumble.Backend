using System.Reflection;
using Microsoft.Extensions.DependencyInjection;

namespace Dumble.BundleManagementService.Application;

public static class DependencyInjection
{
    public static IServiceCollection AddApplication(this IServiceCollection services)
    {
        services.AddMediatR(opt => opt.RegisterServicesFromAssembly(Assembly.GetExecutingAssembly()));
        
        return services;
    }
}