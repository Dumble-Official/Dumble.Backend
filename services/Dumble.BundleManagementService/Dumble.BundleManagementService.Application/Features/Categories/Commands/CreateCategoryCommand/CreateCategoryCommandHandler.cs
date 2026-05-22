using Dumble.BundleManagementService.Application.Contracts.Repositories;
using Dumble.BundleManagementService.Domain.CategoryAggregate;
using Dumble.BundleManagementService.Domain.CategoryAggregate.ValueObjects;
using Dumble.SharedKernel.Contracts;
using Dumble.SharedKernel.Enums;
using MediatR;

namespace Dumble.BundleManagementService.Application.Features.Categories.Commands.CreateCategoryCommand;

public sealed class CreateCategoryCommandHandler(
    IGenericRepository<Category, CategoryId> categoryRepository,
    ILoggedInUserService loggedInUserService
    ) : IRequestHandler<CreateCategoryCommand, CategoryId>
{
    public async Task<CategoryId> Handle(CreateCategoryCommand request, CancellationToken cancellationToken)
    {
        var user = loggedInUserService.GetCurrentUser();
        if (!user.IsInRole(UserType.Admin))
            throw new UnauthorizedAccessException("Only administrators can create categories");

        var category = Category.Create(Name.Create(request.Name));

        await categoryRepository.Create(category);
        await categoryRepository.CompleteAsync();

        return category.Id;
    }
}
