using Dumble.BundleManagementService.Application.Contracts;
using Dumble.BundleManagementService.Application.Contracts.Repositories;
using Dumble.BundleManagementService.Domain.CategoryAggregate;
using Dumble.BundleManagementService.Domain.CategoryAggregate.ValueObjects;
using MediatR;

namespace Dumble.BundleManagementService.Application.Features.Categories.Commands.CreateCategoryCommand;

public sealed class CreateCategoryCommandHandler(
    IGenericRepository<Category, CategoryId> categoryRepository,
    ILoggedInUserService loggedInUserService
    ) : IRequestHandler<CreateCategoryCommand, CategoryId>
{
    public async Task<CategoryId> Handle(CreateCategoryCommand request, CancellationToken cancellationToken)
    {
        // 1. Check if he is an admin or not.
        var user = loggedInUserService.GetCurrentUser();

        var isAdmin = user.Roles.Any(role => role == "Admin");

        if (!isAdmin) throw new Exception("UnAuthorized");
        
        // 2. Create Category 
        var category = Category.Create(
            Name.Create(request.Name)
            );

        // 3. Persist Changes to the database
        await categoryRepository.Create(category);

        await categoryRepository.CompleteAsync();

        return category.Id;
    }
}